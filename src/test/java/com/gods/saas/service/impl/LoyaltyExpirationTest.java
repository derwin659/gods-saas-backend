package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateLoyaltySettingsRequest;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoyaltyExpirationTest {
    private final TenantSettingsRepository settingsRepo = mock(TenantSettingsRepository.class);
    private final LoyaltyPointLotRepository lots = mock(LoyaltyPointLotRepository.class);
    private final LoyaltyAccountRepository accounts = mock(LoyaltyAccountRepository.class);
    private final LoyaltyMovementRepository movements = mock(LoyaltyMovementRepository.class);
    private final TenantSettings settings = new TenantSettings();
    private final OwnerLoyaltySettingsService config = new OwnerLoyaltySettingsService(lots, settingsRepo);
    private final LoyaltyServiceImpl service = new LoyaltyServiceImpl(accounts, movements, lots, mock(CustomerRepository.class), settingsRepo);

    private LoyaltyPointLot lot(int points) {
        return LoyaltyPointLot.builder().id(1L).tenantId(10L).customerId(20L).pointsAvailable(points)
                .pointsEarned(points).status("ACTIVE").expiresAt(LocalDateTime.now().minusDays(1)).build();
    }
    private void settings(boolean enabled) {
        settings.setScheduleConfig(new HashMap<>(Map.of(OwnerLoyaltySettingsService.EXPIRATION_ENABLED_KEY, enabled)));
        when(settingsRepo.findByTenant_Id(10L)).thenReturn(Optional.of(settings));
    }
    @Test void defaultsToNoExpirationAndSkipsLegacyExpiredLots() {
        when(settingsRepo.findByTenant_Id(10L)).thenReturn(Optional.of(settings));
        assertFalse(config.getSettings(10L).getPointsExpirationEnabled());
        when(lots.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any())).thenReturn(List.of(lot(100)));
        assertEquals(0, service.expirePoints());
        verifyNoInteractions(accounts, movements);
    }
    @Test void enablingGivesExistingPointsFullPeriodAndDoesNotTouchOtherTenants() {
        settings(false);
        var old = lot(100);
        when(lots.findByTenantIdAndStatus(10L, "ACTIVE")).thenReturn(List.of(old));
        var request = new UpdateLoyaltySettingsRequest();
        request.setPointsExpirationEnabled(true); request.setPointsExpirationDays(365);
        var before = LocalDateTime.now().plusDays(365);
        assertTrue(config.updateSettings(10L, request).getPointsExpirationEnabled());
        assertFalse(old.getExpiresAt().isBefore(before));
        assertTrue(old.getExpiresAt().isBefore(before.plusSeconds(5)));
        verify(lots).findByTenantIdAndStatus(10L, "ACTIVE");
    }
    @Test void unrelatedSaveDoesNotRenewDeadlinesAndOldClientsPreservePolicy() {
        settings(true);
        config.updateSettings(10L, new UpdateLoyaltySettingsRequest());
        verifyNoInteractions(lots);
        assertTrue(config.getSettings(10L).getPointsExpirationEnabled());
    }
    @Test void rejectsInvalidDays() {
        settings(false);
        for (int days : new int[]{0, -1, 3651}) {
            var request = new UpdateLoyaltySettingsRequest(); request.setPointsExpirationDays(days);
            assertThrows(RuntimeException.class, () -> config.updateSettings(10L, request));
        }
        verify(settingsRepo, never()).save(any());
    }
    @Test void expiresOnlyRemainingPointsAndPreservesAccumulatedPoints() {
        settings(true);
        var lot = lot(100); lot.setPointsAvailable(40);
        var account = new LoyaltyAccount(); account.setId(3L);
        account.setPuntosDisponibles(70); account.setPuntosAcumulados(200);
        when(lots.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any())).thenReturn(List.of(lot));
        when(accounts.findByTenant_IdAndCustomer_Id(10L,20L)).thenReturn(Optional.of(account));
        assertEquals(1,service.expirePoints());
        assertEquals(30,account.getPuntosDisponibles());
        assertEquals(200,account.getPuntosAcumulados());
        verify(movements).save(argThat(m -> m.getPuntos() == -40 && m.getSaldoResultante() == 30));
        assertEquals(0,lot.getPointsAvailable());
        assertEquals("EXPIRED",lot.getStatus());
    }
    @Test void consumedPointsCannotExpireAgain() {
        settings(true);
        var first = lot(100); var second = lot(100); second.setId(2L);
        when(lots.findByTenantIdAndCustomerIdAndStatusOrderByExpiresAtAsc(10L,20L,"ACTIVE"))
                .thenReturn(List.of(first, second));
        new LoyaltyLotConsumptionService(lots).consume(10L,20L,130);
        assertEquals(0,first.getPointsAvailable()); assertEquals(70,second.getPointsAvailable());
        var account = new LoyaltyAccount(); account.setPuntosDisponibles(70);
        when(lots.findByStatusAndExpiresAtBefore(eq("ACTIVE"),any())).thenReturn(List.of(first,second));
        when(accounts.findByTenant_IdAndCustomer_Id(10L,20L)).thenReturn(Optional.of(account));
        assertEquals(1,service.expirePoints());
        verify(movements, times(1)).save(argThat(m -> m.getPuntos() == -70));
        assertEquals(0,account.getPuntosDisponibles());
    }
    @Test void disablingStopsOverduePointsImmediately() {
        settings(true);
        var request = new UpdateLoyaltySettingsRequest(); request.setPointsExpirationEnabled(false);
        config.updateSettings(10L,request);
        when(lots.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any())).thenReturn(List.of(lot(100)));
        assertEquals(0,service.expirePoints()); verifyNoInteractions(accounts, movements);
    }
}
