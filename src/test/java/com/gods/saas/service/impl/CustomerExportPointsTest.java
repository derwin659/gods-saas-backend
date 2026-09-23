package com.gods.saas.service.impl;

import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.projection.CustomerExportProjection;
import org.springframework.data.jpa.repository.Query;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import java.io.ByteArrayInputStream;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerExportPointsTest {
    @Test
    void queryUsesCurrentAvailableBalanceAndKeepsTenantIsolation() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:exportpoints;MODE=PostgreSQL");
             var st = connection.createStatement()) {
            st.execute("create table customer(customer_id bigint, tenant_id bigint, nombres varchar, apellidos varchar, telefono varchar, email varchar, fecha_registro timestamp, puntos_disponibles int, activo boolean)");
            st.execute("create table loyalty_account(customer_id bigint, tenant_id bigint, puntos_disponibles int, puntos_acumulados int)");
            st.execute("create table sale(customer_id bigint, tenant_id bigint, branch_id bigint, sale_date timestamp, fecha_creacion timestamp, payment_validation_status varchar)");
            st.execute("create table branch(branch_id bigint, nombre varchar)");
            st.execute("insert into customer(customer_id, tenant_id, nombres, puntos_disponibles) values (1,10,'A',0),(2,10,'B',999),(3,10,'C',999),(4,20,'D',0)");
            st.execute("insert into loyalty_account values (1,10,145,300),(2,10,0,200),(3,20,777,777),(4,20,80,80)");
            var sql = CustomerRepository.class.getMethod("exportCustomers", Long.class).getAnnotation(Query.class).value().replace(":tenantId", "10");
            Map<Long,Integer> points = new HashMap<>();
            try (var rs = st.executeQuery(sql)) {
                while (rs.next()) points.put(rs.getLong("customerId"), rs.getInt("puntos"));
            }
            assertEquals(Map.of(1L,145,2L,0,3L,0), points);
        }
    }

    @Test
    void workbookWritesPointsAsNumericCells() throws Exception {
        var repository = mock(CustomerRepository.class);
        var customer = mock(CustomerExportProjection.class);
        when(customer.getCustomerId()).thenReturn(1L);
        when(customer.getPuntos()).thenReturn(145);
        when(repository.exportCustomers(10L)).thenReturn(List.of(customer));
        byte[] bytes = new CustomerExportService(repository).exportXlsx(10L);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var cell = book.getSheet("Clientes").getRow(1).getCell(8);
            assertEquals(CellType.NUMERIC, cell.getCellType());
            assertEquals(145, cell.getNumericCellValue());
        }
    }
}
