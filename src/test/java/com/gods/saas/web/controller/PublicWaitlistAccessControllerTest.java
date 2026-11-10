package com.gods.saas.web.controller;

import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicWaitlistAccessControllerTest {
    final TenantRepository tenants = mock(TenantRepository.class);
    final CustomerService customers = mock(CustomerService.class);
    final PublicWaitlistAccessController controller = new PublicWaitlistAccessController(tenants, customers);
    @Test void disabledDoesNotSendOtpOrQueryTenant() {
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> controller.access("GODS", new PublicWaitlistAccessController.AccessRequest("+51999999999", "es"))).getStatusCode().value());
        verifyNoInteractions(tenants, customers);
    }
    @Test void businessComesFromActivePublicCodeNotCallerTenantId() {
        ReflectionTestUtils.setField(controller, "enabled", true);
        when(tenants.findByCodigoIgnoreCaseAndActiveTrue("GODS")).thenReturn(Optional.of(Tenant.builder().id(7L).build()));
        when(customers.requestLoginOtp(7L, "+51999999999", "es", null)).thenReturn(new CustomerService.OtpDispatch(12L, 300, "sms"));
        var response = controller.access(" GODS ", new PublicWaitlistAccessController.AccessRequest("+51999999999", "es"));
        assertEquals(7L, response.get("tenantId")); assertEquals(12L, response.get("otpId"));
        assertFalse(response.containsKey("token")); assertFalse(response.containsKey("customerId"));
        verify(customers).requestLoginOtp(7L, "+51999999999", "es", null);
    }
    @Test void unknownOrInactiveBusinessNeverSendsCode() {
        ReflectionTestUtils.setField(controller, "enabled", true);
        when(tenants.findByCodigoIgnoreCaseAndActiveTrue("OTHER")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.access("OTHER", new PublicWaitlistAccessController.AccessRequest("+51999999999", "es"))).getStatusCode().value());
        verifyNoInteractions(customers);
    }
    @Test void registrationRequiresExplicitConsentBeforeCreatingCustomer() {
        ReflectionTestUtils.setField(controller, "enabled", true);
        when(tenants.findByCodigoIgnoreCaseAndActiveTrue("GODS")).thenReturn(Optional.of(Tenant.builder().id(7L).build()));
        assertThrows(ResponseStatusException.class, () -> controller.register("GODS", new PublicWaitlistAccessController.RegistrationRequest("+51999999999", "es", "Ana", "", false)));
        verifyNoInteractions(customers);
    }
    @Test void registrationCreatesUnverifiedAccountWithoutReturningAccessToken() {
        ReflectionTestUtils.setField(controller, "enabled", true);
        when(tenants.findByCodigoIgnoreCaseAndActiveTrue("GODS")).thenReturn(Optional.of(Tenant.builder().id(7L).build()));
        assertEquals(java.util.Map.of("created", true), controller.register("GODS", new PublicWaitlistAccessController.RegistrationRequest("+51999999999", "es", " Ana ", "Perez", true)));
        verify(customers).registerFromApp(7L, "+51999999999", null, "Ana", "Perez", "es");
        verify(customers, never()).requestLoginOtp(any(), any(), any(), any());
    }
    @Test void duplicateAccountRejectionIsPreservedWithoutUpdatingCustomer() {
        ReflectionTestUtils.setField(controller, "enabled", true);
        when(tenants.findByCodigoIgnoreCaseAndActiveTrue("GODS")).thenReturn(Optional.of(Tenant.builder().id(7L).build()));
        when(customers.registerFromApp(any(), any(), any(), any(), any(), any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> controller.register("GODS", new PublicWaitlistAccessController.RegistrationRequest("+51999999999", "es", "Ana", "", true))).getStatusCode().value());
    }}
