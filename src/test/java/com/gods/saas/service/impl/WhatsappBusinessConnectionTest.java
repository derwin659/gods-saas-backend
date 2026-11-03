package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsappBusinessConnectionTest {
    final TenantSettingsRepository repository=mock(TenantSettingsRepository.class);
    final WhatsappBusinessSenderService senders=new WhatsappBusinessSenderService(repository);
    final String centralAccount="AC"+"c".repeat(32),accountA="AC"+"a".repeat(32),accountB="AC"+"b".repeat(32);
    final String tokenA="1".repeat(32),tokenB="2".repeat(32),centralToken="3".repeat(32);
    final String phoneA="+51911111111",phoneB="+51922222222",shared="+15005550006";
    final String webhook="https://example.test/api/public/whatsapp/twilio/inbound";
    final ObjectMapper mapper=new ObjectMapper();
    Map<String,Object> connection(String phone,String sid,String token) {
        return Map.of("phone",phone,"accountSid",sid,"authToken",token,"status","CONNECTED",
                "depositContentSid","HX"+"d".repeat(32));
    }
    void provision(Map<String,Object> connections) throws Exception {
        ReflectionTestUtils.setField(senders,"businessConnections",mapper.writeValueAsString(connections));
    }
    TenantSettings selected(Long tenant,String phone) {
        TenantSettings settings=new TenantSettings();
        settings.setScheduleConfig(new HashMap<>(Map.of(WhatsappBusinessSenderService.MODE_KEY,"BUSINESS",WhatsappBusinessSenderService.PHONE_KEY,phone)));
        when(repository.findByTenant_Id(tenant)).thenReturn(Optional.of(settings));return settings;
    }
    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(senders,"sharedPhone",shared);
        ReflectionTestUtils.setField(senders,"sharedAccountSid",centralAccount);
        ReflectionTestUtils.setField(senders,"enabled",true);
        selected(1L,phoneA);selected(2L,phoneB);
        provision(Map.of("1",connection(phoneA,accountA,tokenA),"2",connection(phoneB,accountB,tokenB)));
    }
    @Test void onlyServerApprovedPhoneForThisBusinessBecomesConnected() {
        assertTrue(senders.selection(1L).businessConnected());
        selected(1L,phoneB);
        assertEquals("PENDING",senders.selection(1L).businessConnectionStatus());
        assertEquals(shared,senders.selection(1L).effectiveSenderPhone());
        assertTrue(senders.inbound("whatsapp:"+phoneA).isEmpty());
    }
    @Test void duplicateSenderOrSubaccountFailsClosed() throws Exception {
        provision(Map.of("1",connection(phoneA,accountA,tokenA),"2",connection(phoneB,accountA,tokenB)));
        assertTrue(senders.connection(1L).isEmpty());
        provision(Map.of("1",connection(phoneA,accountA,tokenA),"2",connection(phoneA,accountB,tokenB)));
        assertTrue(senders.connection(2L).isEmpty());
        provision(Map.of("1",connection(phoneA,centralAccount,tokenA)));
        assertFalse(senders.selection(1L).businessConnected());
    }
    @Test void disabledSignatureValidationCannotActivateBusinessNumbers() {
        ReflectionTestUtils.setField(senders,"validateSignature",false);
        assertFalse(senders.selection(1L).businessConnected());
        assertTrue(senders.inbound("whatsapp:"+phoneA).isEmpty());
    }
    TwilioWebhookSignatureValidator validator() {
        var validator=new TwilioWebhookSignatureValidator(senders);
        ReflectionTestUtils.setField(validator,"authToken",centralToken);
        ReflectionTestUtils.setField(validator,"accountSid",centralAccount);
        ReflectionTestUtils.setField(validator,"inboundWebhookUrl",webhook);
        ReflectionTestUtils.setField(validator,"validateSignature",true);return validator;
    }
    Map<String,List<String>> form(String phone,String account) {
        return Map.of("To",List.of("whatsapp:"+phone),"AccountSid",List.of(account),"From",List.of("whatsapp:+51999999999"),"Body",List.of("hola"));
    }
    String signature(Map<String,List<String>> form,String token) throws Exception {
        StringBuilder text=new StringBuilder(webhook);
        for(var field:new TreeMap<>(form).entrySet()) {
            var values=new ArrayList<>(field.getValue());Collections.sort(values);
            for(String value:values)text.append(field.getKey()).append(value);
        }
        Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(text.toString().getBytes(StandardCharsets.UTF_8)));
    }
    @Test void validatesExactSubaccountAndDestinationWithoutSharedTokenFallback() throws Exception {
        var form=form(phoneA,accountA);var validator=validator();
        assertTrue(validator.isValid(signature(form,tokenA),form));
        assertFalse(validator.isValid(signature(form,centralToken),form));
        var wrongAccount=form(phoneA,accountB);
        assertFalse(validator.isValid(signature(wrongAccount,tokenB),wrongAccount));
        var central=form(shared,centralAccount);
        assertTrue(validator.isValid(signature(central,centralToken),central));
    }
    @Test void rejectsUnknownAndAmbiguousDestinationsEvenWithValidSignature() throws Exception {
        var unknown=form("+51933333333",accountA);
        assertFalse(validator().isValid(signature(unknown,tokenA),unknown));
        var ambiguous=new HashMap<>(form(phoneA,accountA));ambiguous.put("To",List.of("whatsapp:"+phoneA,"whatsapp:"+phoneB));
        assertFalse(validator().isValid(signature(ambiguous,tokenA),ambiguous));
    }
    @Test void depositNoticeUsesOriginSubaccountAndOwnApprovedTemplate() throws Exception {
        var sender=new TwilioDepositNoticeSender(mapper,senders);
        var outbound=sender.outbound(1L,phoneA);
        assertEquals(accountA,outbound.accountSid());assertEquals(tokenA,outbound.authToken());
        var form=sender.messageForm("+51999999999","Aprobado",Instant.now().minusSeconds(90000),outbound);
        assertEquals("whatsapp:"+phoneA,form.getFirst("From"));
        assertEquals("HX"+"d".repeat(32),form.getFirst("ContentSid"));
        assertThrows(IllegalStateException.class,()->sender.outbound(2L,phoneA));
        provision(Map.of());
        assertThrows(IllegalStateException.class,()->sender.outbound(1L,phoneA));
    }
    @Test void receiptSourceIsBoundToBusinessAndMessageWithoutNetworkAccess() {
        var downloader=new TwilioDepositMediaDownloader(senders);
        String sid="SM"+"e".repeat(32);
        String url="https://api.twilio.com/2010-04-01/Accounts/"+accountA+"/Messages/"+sid+"/Media/ME"+"f".repeat(32);
        assertTrue(downloader.validSource(url,sid,1L,phoneA));
        assertFalse(downloader.validSource(url.replace(accountA,accountB),sid,1L,phoneA));
        assertFalse(downloader.validSource(url,sid,2L,phoneA));
        assertFalse(downloader.validSource(url,"SM"+"0".repeat(32),1L,phoneA));
    }
    @Test void noCredentialIsExposedInSettingsOrDiagnosticString() throws Exception {
        var settings=new OwnerWhatsappSettingsService(repository,mock(CentralWhatsappSenderService.class),senders).getSettings(1L);
        String json=mapper.writeValueAsString(settings);
        assertFalse(json.contains(tokenA));assertFalse(json.contains(accountA));assertFalse(json.contains("authToken"));
        assertFalse(senders.connection(1L).orElseThrow().toString().contains(tokenA));
    }
    @Test void customerNotificationsUseOwnSenderButOperationalAlertsKeepCentralSender() {
        var twilio=mock(WhatsappNotificationSenderTwilioImpl.class);
        var central=mock(CentralWhatsappSenderService.class);
        var router=new WhatsappNotificationSenderRouter(repository,mock(WhatsappNotificationSenderMockImpl.class),
                mock(WhatsappNotificationSenderMetaCloudImpl.class),twilio,central,senders);
        var notification=Notification.builder().tenant(new Tenant(1L)).type(com.gods.saas.domain.enums.NotificationType.BOOKING_REMINDER_60).build();
        when(twilio.send(notification)).thenReturn("OWN");
        assertEquals("OWN",router.send(notification));
        verifyNoInteractions(central);
        notification.setType(com.gods.saas.domain.enums.NotificationType.BOOKING_CREATED);
        notification.setUser(new AppUser());
        when(central.sendBooking(notification)).thenReturn("CENTRAL");
        assertEquals("CENTRAL",router.send(notification));
    }
}
