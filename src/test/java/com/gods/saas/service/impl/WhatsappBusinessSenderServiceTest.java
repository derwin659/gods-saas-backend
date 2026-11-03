package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateWhatsappSettingsRequest;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsappBusinessSenderServiceTest {
    final TenantSettingsRepository repository=mock(TenantSettingsRepository.class);
    final CentralWhatsappSenderService central=mock(CentralWhatsappSenderService.class);
    final WhatsappBusinessSenderService senders=new WhatsappBusinessSenderService(repository);
    final OwnerWhatsappSettingsService service=new OwnerWhatsappSettingsService(repository,central,senders);
    final TenantSettings settings=new TenantSettings();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(senders,"sharedPhone","+15005550006");
        settings.setScheduleConfig(new HashMap<>(Map.of("unrelatedSetting",true)));
        when(repository.findByTenant_Id(1L)).thenReturn(Optional.of(settings));
    }
    @Test void existingBusinessKeepsSharedNumberByDefault() {
        var response=service.getSettings(1L);
        assertEquals("SHARED",response.getSenderMode());
        assertEquals("NOT_REQUESTED",response.getBusinessConnectionStatus());
        assertEquals("+15005550006",response.getEffectiveSenderPhone());
    }
    @Test void ownNumberRequestIsSavedPendingWithoutChangingEffectiveSender() {
        var request=new UpdateWhatsappSettingsRequest();
        request.setSenderMode("BUSINESS");request.setBusinessPhone("+51 911-111-111");
        var response=service.updateSettings(1L,request);
        assertEquals("BUSINESS",response.getSenderMode());
        assertEquals("+51911111111",response.getBusinessPhone());
        assertEquals("PENDING",response.getBusinessConnectionStatus());
        assertEquals("SHARED",response.getEffectiveSenderMode());
        assertEquals("+15005550006",response.getEffectiveSenderPhone());
        assertEquals(true,settings.getScheduleConfig().get("unrelatedSetting"));
        verify(repository).save(settings);
    }
    @Test void ownerCannotDeclareAnOwnNumberConnected() {
        var request=new UpdateWhatsappSettingsRequest();
        request.setSenderMode("BUSINESS");request.setBusinessPhone("+51911111111");
        request.setConnectionStatus("CONNECTED");request.setProvider("TWILIO");
        assertEquals("PENDING",service.updateSettings(1L,request).getBusinessConnectionStatus());
    }
    @Test void returningToSharedRetainsPhoneForLaterWithoutRequestingActivation() {
        settings.getScheduleConfig().put(WhatsappBusinessSenderService.PHONE_KEY,"+51911111111");
        settings.getScheduleConfig().put(WhatsappBusinessSenderService.MODE_KEY,"BUSINESS");
        var request=new UpdateWhatsappSettingsRequest();request.setSenderMode("SHARED");
        var response=service.updateSettings(1L,request);
        assertEquals("SHARED",response.getSenderMode());
        assertEquals("NOT_REQUESTED",response.getBusinessConnectionStatus());
        assertEquals("+51911111111",response.getBusinessPhone());
    }
    @Test void missingOrInvalidPhoneCannotCreateConnectionRequest() {
        for(String raw:List.of("","911111111","++51911111111","+51911111111@evil")) {
            var request=new UpdateWhatsappSettingsRequest();
            request.setSenderMode("BUSINESS");request.setBusinessPhone(raw);
            assertThrows(IllegalArgumentException.class,()->service.updateSettings(1L,request));
        }
        verify(repository,never()).save(any());
    }
    @Test void invalidModeCannotBeSaved() {
        var request=new UpdateWhatsappSettingsRequest();request.setSenderMode("UNKNOWN");
        assertThrows(IllegalArgumentException.class,()->service.updateSettings(1L,request));
        verify(repository,never()).save(any());
    }
}
