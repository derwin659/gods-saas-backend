package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.SaleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BarberPackageSaleTest {
    @Test void replacesOriginalServiceWithOnePackageAndKeepsAssignedProfessional() {
        var appointments=mock(AppointmentRepository.class);
        var services=mock(ServiceRepository.class);
        var sales=mock(SaleService.class);
        var assignments=mock(BarberServiceAssignmentService.class);
        var target=new BarberSaleService(appointments,mock(ProductRepository.class),mock(ProductBranchStockRepository.class),services,sales,mock(CustomerCutHistoryService.class),assignments);
        var original=ServiceEntity.builder().id(2L).nombre("Corte").precio(30.0).build();
        var pack=ServiceEntity.builder().id(10L).nombre("Corte y barba").precio(45.0).activo(true).packageServiceIds(List.of(2L,3L)).build();
        var appointment=new Appointment(); appointment.setId(20L); appointment.setService(original); appointment.setEstado("CONFIRMADO");
        when(appointments.findByIdAndTenant_IdAndBranch_IdAndUser_Id(20L,1L,4L,5L)).thenReturn(Optional.of(appointment));
        when(services.findByIdAndTenant_IdAndDeletedAtIsNull(10L,1L)).thenReturn(Optional.of(pack));
        when(assignments.canPerform(1L,4L,5L,10L)).thenReturn(true);
        when(sales.crearVenta(any())).thenReturn(SaleResponse.builder().saleId(99L).total(new BigDecimal("45.00")).build());
        var request=new CreateSaleFromAppointmentRequest();request.setAppointmentId(20L);request.setPackageServiceId(10L);request.setMetodoPago("EFECTIVO");request.setCashReceived(50.0);
        var response=target.createSaleFromAppointment(1L,4L,5L,request);
        var capture=ArgumentCaptor.forClass(CreateSaleRequest.class);verify(sales).crearVenta(capture.capture());
        assertEquals(1,capture.getValue().getItems().size());
        var item=capture.getValue().getItems().getFirst();
        assertEquals(10L,item.getServiceId());assertEquals(5L,item.getBarberUserId());assertEquals(45.0,item.getPrecioUnitario());
        assertEquals("Corte y barba",response.getServicio());
        assertEquals("COMPLETADO",appointment.getEstado());
        assertEquals(2L,appointment.getService().getId());
        when(assignments.canPerform(1L,4L,5L,10L)).thenReturn(false);appointment.setEstado("CONFIRMADO");
        assertThrows(IllegalArgumentException.class,()->target.createSaleFromAppointment(1L,4L,5L,request));
        verify(sales,times(1)).crearVenta(any());
    }
}
