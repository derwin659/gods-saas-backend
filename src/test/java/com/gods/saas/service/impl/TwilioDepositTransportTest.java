package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class TwilioDepositTransportTest {
    final String account="AC"+"a".repeat(32),message="SM"+"b".repeat(32),media="ME"+"c".repeat(32);
    @Test void downloadIsBoundToConfiguredAccountAndSpecificInboundMessageWithoutNetworkAccess(){
        var downloader=new TwilioDepositMediaDownloader();ReflectionTestUtils.setField(downloader,"accountSid",account);
        String valid="https://api.twilio.com/2010-04-01/Accounts/"+account+"/Messages/"+message+"/Media/"+media;
        assertTrue(downloader.validSource(valid,message));
        for(String invalid:new String[]{valid.replace("https:","http:"),valid.replace("api.twilio.com","evil.example"),
                valid.replace(account,"AC"+"d".repeat(32)),valid.replace(message,"SM"+"e".repeat(32)),valid+"?url=http://127.0.0.1",valid.replace("api.twilio.com","user@api.twilio.com"),valid.replace("api.twilio.com","api.twilio.com:8443")})
            assertFalse(downloader.validSource(invalid,message));
    }
    TwilioDepositNoticeSender sender(){
        var sender=new TwilioDepositNoticeSender(new ObjectMapper());
        ReflectionTestUtils.setField(sender,"accountSid",account);ReflectionTestUtils.setField(sender,"token","secret-not-printed");ReflectionTestUtils.setField(sender,"from","+15005550006");
        return sender;
    }
    @Test void recentConversationUsesTransactionalBodyAndCorrectWhatsAppAddresses() throws Exception {
        var form=sender().messageForm("+51999999999","Adelanto aprobado",Instant.now());
        assertEquals("whatsapp:+51999999999",form.getFirst("To"));assertEquals("whatsapp:+15005550006",form.getFirst("From"));
        assertEquals("Adelanto aprobado",form.getFirst("Body"));assertNull(form.getFirst("ContentSid"));
    }
    @Test void oldConversationRequiresTemplateAndNeverFallsBackToUnapprovedFreeText() throws Exception {
        var sender=sender();Instant yesterday=Instant.now().minus(Duration.ofHours(25));
        assertThrows(IllegalStateException.class,()->sender.messageForm("+51999999999","Adelanto aprobado",yesterday));
        ReflectionTestUtils.setField(sender,"contentSid","HX"+"a".repeat(32));
        var form=sender.messageForm("+51999999999","Reserva 99 aprobada",yesterday);
        assertNull(form.getFirst("Body"));assertNotNull(form.getFirst("ContentSid"));
        assertEquals("Reserva 99 aprobada",new ObjectMapper().readTree(form.getFirst("ContentVariables")).path("1").asText());
    }
}
