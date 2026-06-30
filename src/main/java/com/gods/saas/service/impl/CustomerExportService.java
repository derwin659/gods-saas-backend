package com.gods.saas.service.impl;

import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.projection.CustomerExportProjection;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerExportService {
    private final CustomerRepository customerRepository;
    public byte[] exportXlsx(Long tenantId) {
        List<CustomerExportProjection> rows = customerRepository.exportCustomers(tenantId);
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Clientes");
            String[] headers = {"ID","Nombres","Apellidos","Telefono","Correo","Fecha de registro","Ultima visita","Ultima sede","Puntos","Compras","Estado"};
            CellStyle headerStyle = book.createCellStyle();
            Font font = book.createFont(); font.setBold(true); headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i=0;i<headers.length;i++) { Cell c=header.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(headerStyle); }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int index=1;
            for (CustomerExportProjection item : rows) {
                Row row=sheet.createRow(index++); int c=0;
                row.createCell(c++).setCellValue(item.getCustomerId());
                row.createCell(c++).setCellValue(value(item.getNombres()));
                row.createCell(c++).setCellValue(value(item.getApellidos()));
                row.createCell(c++).setCellValue(value(item.getTelefono()));
                row.createCell(c++).setCellValue(value(item.getEmail()));
                row.createCell(c++).setCellValue(item.getFechaRegistro()==null?"":fmt.format(item.getFechaRegistro()));
                row.createCell(c++).setCellValue(item.getUltimaVisita()==null?"":fmt.format(item.getUltimaVisita()));
                row.createCell(c++).setCellValue(value(item.getSede()));
                row.createCell(c++).setCellValue(item.getPuntos()==null?0:item.getPuntos());
                row.createCell(c++).setCellValue(item.getCompras()==null?0:item.getCompras());
                row.createCell(c).setCellValue(Boolean.FALSE.equals(item.getActivo())?"Inactivo":"Activo");
            }
            int[] widths = {10,22,22,18,28,20,20,22,12,12,12};
            for(int i=0;i<headers.length;i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.createFreezePane(0,1); book.write(out); return out.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("No se pudo generar el Excel de clientes", ex); }
    }
    public long count(Long tenantId) { return customerRepository.exportCustomers(tenantId).size(); }
    private String value(String value) { return value == null ? "" : value; }
}
