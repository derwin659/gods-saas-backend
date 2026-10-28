package com.gods.saas.service.impl;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostSaleWhatsappAssistantInvitationTest {
    final WhatsappAssistantService assistant=mock(WhatsappAssistantService.class);
    final PostSaleWhatsappAssistantInvitation invitation=new PostSaleWhatsappAssistantInvitation(assistant);
    @Test void usesActualBusinessCodeAndCentralChatLink() {
        when(assistant.status(2L)).thenReturn(Map.of("enabled",true,"businessCode","INDYSTRY","bookingWhatsappUrl","https://wa.me/51907343909?text=GODS+INDYSTRY"));
        String message=invitation.build(2L);assertTrue(message.contains("GODS INDYSTRY"));
        assertTrue(message.contains("https://wa.me/51907343909?text=GODS+INDYSTRY"));verify(assistant).status(2L);
    }
    @Test void businessWhoseCodeIsGodsReceivesFullCommand() {
        when(assistant.status(2L)).thenReturn(Map.of("enabled",true,"businessCode","GODS","bookingWhatsappUrl","https://wa.me/51907343909?text=GODS+GODS"));
        assertTrue(invitation.build(2L).contains("envia: GODS GODS"));
    }
    @Test void disabledBusinessNeverAdvertisesUnavailableAssistant() {
        when(assistant.status(5L)).thenReturn(Map.of("enabled",false,"businessCode","OTRO","bookingWhatsappUrl",""));
        assertEquals("",invitation.build(5L));
    }
    @Test void missingSenderLinkNeverInvitesToUnreachableChat() {
        when(assistant.status(2L)).thenReturn(Map.of("enabled",true,"businessCode","GODS","bookingWhatsappUrl",""));
        assertEquals("",invitation.build(2L));
    }
}
