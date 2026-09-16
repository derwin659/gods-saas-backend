package com.gods.saas.service;

import com.gods.saas.domain.dto.ActualizarClienteRequest;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.service.impl.*;
import com.gods.saas.utils.JwtUtil;
import com.gods.saas.web.controller.OwnerCustomerController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerPhonePrivacyTest {
    private void verifyUpdate(boolean allowed, String submittedPhone) {
        var service = mock(CustomerService.class);
        var jwt = mock(JwtUtil.class);
        var permissions = mock(AdminPermissionService.class);
        when(jwt.getTenantIdFromToken("token")).thenReturn(1L);
        when(permissions.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE")).thenReturn(allowed);
        var tenant = new Tenant();
        tenant.setId(1L);
        var customer = new Customer();
        customer.setId(2L);
        customer.setTenant(tenant);
        customer.setTelefono("+51987654321");
        var request = new ActualizarClienteRequest();
        request.setNombre("Nombre corregido");
        request.setApellido("Apellido corregido");
        request.setTelefono(submittedPhone);
        when(service.actualizarCliente(eq(1L), eq(2L), any())).thenAnswer(invocation -> {
            ActualizarClienteRequest saved = invocation.getArgument(2);
            assertEquals("Nombre corregido", saved.getNombre());
            assertEquals("Apellido corregido", saved.getApellido());
            assertEquals(allowed ? submittedPhone : null, saved.getTelefono());
            return customer;
        });
        var controller = new OwnerCustomerController(service, jwt, permissions,
                mock(CustomerExportService.class), mock(GeneralAuditService.class));
        var response = controller.update("Bearer token", 2L, request).getBody();
        assertNotNull(response);
        assertEquals(!allowed, response.isPhoneHidden());
        assertEquals(allowed ? "+51987654321" : "Teléfono oculto", response.getPhone());
        verify(permissions).checkPermission("CUSTOMERS_ACCESS");
        verify(service).actualizarCliente(1L, 2L, request);
    }
    @Test void restrictedAdminCanEditNamesWithoutPhone() { verifyUpdate(false, null); }
    @Test void restrictedAdminCannotReplacePhoneEvenWithForgedRequest() { verifyUpdate(false, "+51911111111"); }
    @Test void legacyMaskedPhoneIsNeverSaved() { verifyUpdate(false, "****4321"); }
    @Test void authorizedUserCanStillEditPhone() { verifyUpdate(true, "+51911111111"); }
}