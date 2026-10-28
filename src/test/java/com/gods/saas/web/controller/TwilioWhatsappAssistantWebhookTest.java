package com.gods.saas.web.controller;

import com.gods.saas.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TwilioWhatsappAssistantWebhookTest {
    private final TwilioWebhookSignatureValidator validator = mock(TwilioWebhookSignatureValidator.class);
    private final OwnerWhatsappPhoneVerificationService verification = mock(OwnerWhatsappPhoneVerificationService.class);
    private final WhatsappAssistantService assistant = mock(WhatsappAssistantService.class);
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TwilioWhatsappWebhookController(validator,verification,assistant)).build();
        when(validator.isValid(any(),any())).thenReturn(true);
        when(verification.verifyInbound(any(),any())).thenReturn(new OwnerWhatsappPhoneVerificationService.InboundVerificationResult(false,null));
    }
    @Test void invalidSignatureNeverReachesAssistant() throws Exception {
        when(validator.isValid(any(),any())).thenReturn(false);
        mvc.perform(post("/api/public/whatsapp/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("Body","puntos"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(assistant,verification);
    }
    @Test void responseEscapesCatalogTextAsXml() throws Exception {
        when(assistant.reply(any(),any(),any(),any())).thenReturn("Corte & barba <hoy>");
        mvc.perform(post("/api/public/whatsapp/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From","whatsapp:+51999999999").param("To","whatsapp:+15005550006").param("MessageSid","SM123").param("Body","promos"))
            .andExpect(status().isOk()).andExpect(content().string("<Response><Message>Corte &amp; barba &lt;hoy&gt;</Message></Response>"));
    }
    @Test void ownerVerificationKeepsPriority() throws Exception {
        when(verification.verifyInbound(any(),any())).thenReturn(new OwnerWhatsappPhoneVerificationService.InboundVerificationResult(true,"Verificado"));
        mvc.perform(post("/api/public/whatsapp/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("Body","VERIFICAR GODS 1 123456"))
            .andExpect(status().isOk()).andExpect(content().string("<Response><Message>Verificado</Message></Response>"));
        verifyNoInteractions(assistant);
    }
    @Test void providerFailureReturnsNoInventedBookingOrSensitiveError() throws Exception {
        when(assistant.reply(any(),any(),any(),any())).thenThrow(new IllegalStateException("secret api key"));
        mvc.perform(post("/api/public/whatsapp/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From","whatsapp:+51999999999").param("To","whatsapp:+15005550006").param("MessageSid","SM123").param("Body","reserva"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
