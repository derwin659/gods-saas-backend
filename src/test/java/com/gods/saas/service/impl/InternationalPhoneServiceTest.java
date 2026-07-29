package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternationalPhoneServiceTest {

    private InternationalPhoneService service;

    @BeforeEach
    void setUp() {
        TenantSettingsRepository settingsRepository = mock(TenantSettingsRepository.class);
        when(settingsRepository.findByTenant_Id(anyLong())).thenReturn(Optional.empty());
        service = new InternationalPhoneService(settingsRepository);
    }

    @Test
    void normalizesPeruvianLocalNumberToE164() {
        Tenant tenant = tenant(1L, "Perú");

        InternationalPhoneService.NormalizedPhone result =
                service.normalize(tenant, "999 999 999");

        assertEquals("+51999999999", result.e164());
        assertEquals("PE", result.regionCode());
        assertTrue(result.belongsToTenantRegion());
    }

    @Test
    void preservesColombianInternationalNumberForPeruvianTenant() {
        Tenant tenant = tenant(1L, "PE");

        InternationalPhoneService.NormalizedPhone result =
                service.normalize(tenant, "+57 300 123 4567");

        assertEquals("+573001234567", result.e164());
        assertEquals("CO", result.regionCode());
        assertFalse(result.belongsToTenantRegion());
    }

    @Test
    void supportsEuropeanLocalNumbersUsingTenantCountry() {
        Tenant tenant = tenant(2L, "Reino Unido");

        InternationalPhoneService.NormalizedPhone result =
                service.normalize(tenant, "07911 123456");

        assertEquals("+447911123456", result.e164());
        assertTrue(result.regionCode().equals("GB") || result.regionCode().equals("GG"));
        assertTrue(result.lookupDigits().contains("07911123456"));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.normalize(tenant(3L, "CO"), "123")
        );
    }

    private Tenant tenant(Long id, String country) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setPais(country);
        return tenant;
    }
}
