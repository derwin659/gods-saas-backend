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
    @Test void spanishMenuAndEmojiRoundTripThroughRealXmlParserInUtf8() throws Exception {
        String menu="¿Que deseas hacer mañana?\n1. Promoción de corte & barba 🧔\n2. 'Mis puntos' <consulta>";
        when(assistant.reply(any(),any(),any(),any())).thenReturn(menu);
        var response=mvc.perform(post("/api/public/whatsapp/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("From","whatsapp:+51999999999").param("To","whatsapp:+15005550006").param("MessageSid","SM123").param("Body","hola"))
                .andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals("UTF-8",response.getCharacterEncoding());
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var document=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
        org.junit.jupiter.api.Assertions.assertEquals("Response",document.getDocumentElement().getTagName());
        org.junit.jupiter.api.Assertions.assertEquals(menu,document.getElementsByTagName("Message").item(0).getTextContent());
        org.junit.jupiter.api.Assertions.assertFalse(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("&iquest;"));
    }
    @Test void catalogControlCharactersCannotMakeInvalidXmlOrInjectNewVerbs() throws Exception {
        when(assistant.reply(any(),any(),any(),any())).thenReturn("Corte\u0001\u0000</Message><Redirect>otra-url</Redirect>");
        var response=mvc.perform(post("/api/public/whatsapp/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("From","whatsapp:+51999999999").param("To","whatsapp:+15005550006").param("MessageSid","SM123").param("Body","promos"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var document=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
        org.junit.jupiter.api.Assertions.assertEquals(0,document.getElementsByTagName("Redirect").getLength());
        org.junit.jupiter.api.Assertions.assertEquals("Corte</Message><Redirect>otra-url</Redirect>",document.getElementsByTagName("Message").item(0).getTextContent());
    }
    @Test void signedPhotoWithoutCaptionUsesMediaFlowAndKeepsTheOriginalSignedFields() throws Exception {
        when(assistant.replyWithMedia(any(),any(),any(),any(),any())).thenReturn("Recibi tu captura");
        mvc.perform(post("/api/public/whatsapp/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("From","whatsapp:+51999999999").param("To","whatsapp:+15005550006").param("MessageSid","SM123")
                .param("Body","").param("NumMedia","1").param("MediaUrl0","https://api.twilio.com/media").param("MediaContentType0","image/jpeg"))
                .andExpect(status().isOk()).andExpect(content().string("<Response><Message>Recibi tu captura</Message></Response>"));
        verify(assistant,never()).reply(any(),any(),any(),any());
        verify(assistant).replyWithMedia(eq("whatsapp:+51999999999"),eq("whatsapp:+15005550006"),eq(""),eq("SM123"),argThat(form -> "image/jpeg".equals(form.get("MediaContentType0"))));
    }

    @Test void longPromotionRepliesAreSplitWithoutLosingUnicodeOrXmlCharacters() throws Exception {
        String original="Promocion & corte 🧔\n".repeat(180);
        when(assistant.reply(any(),any(),any(),any())).thenReturn(original);
        var response=mvc.perform(post("/api/public/whatsapp/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("From","whatsapp:+51999999999").param("To","whatsapp:+15005550006").param("MessageSid","SM123").param("Body","3"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var document=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
        var messages=document.getElementsByTagName("Message");
        org.junit.jupiter.api.Assertions.assertTrue(messages.getLength()>1);
        StringBuilder combined=new StringBuilder();
        for(int i=0;i<messages.getLength();i++) {
            String part=messages.item(i).getTextContent();
            org.junit.jupiter.api.Assertions.assertTrue(part.length()<=1400);
            org.junit.jupiter.api.Assertions.assertFalse(part.contains("\uFFFD"));combined.append(part);
        }
        org.junit.jupiter.api.Assertions.assertEquals(original,combined.toString());
    }
}
