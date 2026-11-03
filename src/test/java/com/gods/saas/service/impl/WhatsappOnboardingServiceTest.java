package com.gods.saas.service.impl;
import com.gods.saas.config.WhatsappOnboardingProperties;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsappOnboardingServiceTest {
    final WhatsappOnboardingProperties properties=new WhatsappOnboardingProperties();
    final WhatsappOnboardingProvider provider=mock(WhatsappOnboardingProvider.class);
    final WhatsappBusinessSenderService senders=mock(WhatsappBusinessSenderService.class);
    final WhatsappBusinessConnectionRepository connections=mock(WhatsappBusinessConnectionRepository.class);
    final WhatsappOnboardingSessionRepository sessions=mock(WhatsappOnboardingSessionRepository.class);
    final TenantSettingsRepository settings=mock(TenantSettingsRepository.class);
    final UserTenantRoleRepository roles=mock(UserTenantRoleRepository.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final AtomicReference<WhatsappBusinessConnection> row=new AtomicReference<>();
    final List<WhatsappOnboardingSession> tickets=new ArrayList<>();
    final List<String> events=new ArrayList<>();
    final String phone="+51911111111",waba="123456789",phoneId="987654321",account="AC"+"a".repeat(32),secret="1".repeat(32),sender="XE"+"b".repeat(32);
    WhatsappCredentialCipher cipher;
    WhatsappOnboardingService service;
    @BeforeEach void setup() {
        properties.setEnabled(true);properties.setAppId("123456");properties.setAppSecret("test-only");properties.setConfigId("234567");properties.setSolutionId("345678");properties.setWebUrl("https://example.test");
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher=new WhatsappCredentialCipher(properties);
        service=new WhatsappOnboardingService(properties,cipher,provider,senders,connections,sessions,settings,roles,transactions);
        when(provider.ready()).thenReturn(true);
        when(roles.existsByUserIdAndTenantIdAndRoleIn(anyLong(),anyLong(),anyList())).thenAnswer(i->Long.valueOf(9).equals(i.getArgument(0)) && Long.valueOf(1).equals(i.getArgument(1)));
        when(transactions.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        doAnswer(i->{events.add("commit");return null;}).when(transactions).commit(any());
        var tenant=new TenantSettings();tenant.setScheduleConfig(new HashMap<>(Map.of("otherPreference",true)));
        when(settings.findForWhatsappUpdate(1L)).thenReturn(Optional.of(tenant));
        when(settings.findByTenant_Id(1L)).thenReturn(Optional.of(tenant));
        when(connections.findForUpdate(1L)).thenAnswer(i->Optional.ofNullable(row.get()));
        when(connections.findById(1L)).thenAnswer(i->Optional.ofNullable(row.get()));
        when(connections.saveAndFlush(any())).thenAnswer(i->{var value=(WhatsappBusinessConnection)i.getArgument(0);if(value.getVersion()==null)value.setVersion(0L);row.set(value);events.add("saved:"+value.getState());return value;});
        when(sessions.saveAndFlush(any())).thenAnswer(i->{var value=(WhatsappOnboardingSession)i.getArgument(0);if(!tickets.contains(value))tickets.add(value);return value;});
        when(sessions.findByTicketForUpdate(anyString())).thenAnswer(i->tickets.stream().filter(t->t.getTicketHash().equals(i.getArgument(0))).findFirst());
        when(sessions.findByAccessHash(anyString())).thenAnswer(i->tickets.stream().filter(t->Objects.equals(t.getAccessHash(),i.getArgument(0))).findFirst());
        when(provider.createAccount(1L)).thenAnswer(i->{assertEquals("commit",events.get(events.size()-1));assertEquals("PROVISIONING",row.get().getState());events.add("create");return new WhatsappOnboardingProvider.Account(account,secret);});
        when(provider.register(any(),eq(phone),eq(waba))).thenAnswer(i->{assertEquals("commit",events.get(events.size()-1));assertEquals("REGISTERING",row.get().getState());assertNotEquals(secret,row.get().getEncryptedAuthToken());return new WhatsappOnboardingProvider.Sender(sender,"ONLINE",phone,waba);});
    }
    Map<String,Object> start(){return service.start(1L,9L,phone);}
    String session(){return service.claim((String)start().get("ticket")).get("sessionToken").toString();}
    @Test void launchTicketIsSingleUseAndStoredOnlyAsHash() {
        var launch=start();String ticket=launch.get("ticket").toString();
        assertTrue(launch.get("launchUrl").toString().startsWith("https://example.test/whatsapp/connect#ticket="));
        assertNotEquals(ticket,tickets.get(0).getTicketHash());
        assertEquals(WhatsappOnboardingService.hash(ticket),tickets.get(0).getTicketHash());
        var claimed=service.claim(ticket);assertFalse(claimed.containsKey("authToken"));assertFalse(claimed.containsKey("appSecret"));
        assertThrows(IllegalArgumentException.class,()->service.claim(ticket));
    }
    @Test void expiredTicketAndExpiredCapabilityCannotBeUsed() {
        String ticket=start().get("ticket").toString();tickets.get(0).setTicketExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class,()->service.claim(ticket));
        tickets.get(0).setTicketExpiresAt(Instant.now().plusSeconds(100));
        String access=service.claim(ticket).get("sessionToken").toString();tickets.get(0).setExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class,()->service.complete(access,"code",waba,phoneId));verify(provider,never()).createAccount(any());
    }
    @Test void revokedOwnerCannotProvisionResourcesFromAnOldLink() {
        String access=session();when(roles.existsByUserIdAndTenantIdAndRoleIn(anyLong(),anyLong(),anyList())).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->service.complete(access,"code",waba,phoneId));verify(provider,never()).createAccount(any());
    }
    @Test void onlineRequiresVerifiedMetaPermissionAndMatchingTwilioSender() {
        String access=session();var result=service.complete(access,"meta-code",waba,phoneId);
        assertEquals(true,result.get("connected"));assertEquals("ONLINE",row.get().getState());
        verify(provider).verifyAuthorization("meta-code",waba,phoneId,phone);
        assertEquals(secret,cipher.decrypt(1L,account,row.get().getEncryptedAuthToken()));
        assertFalse(result.toString().contains(secret));
        service.complete(access,"same-code",waba,phoneId);
        verify(provider,times(1)).createAccount(1L);verify(provider,times(1)).register(any(),eq(phone),eq(waba));
    }
    @Test void unknownCreateOutcomeIsNeverRetriedBlindly() {
        String access=session();doThrow(new WhatsappOnboardingProvider.Failure(true)).when(provider).createAccount(1L);
        assertEquals("REVIEW_REQUIRED",service.complete(access,"code",waba,phoneId).get("state"));
        service.complete(access,"code",waba,phoneId);
        verify(provider,times(1)).createAccount(1L);verify(provider,never()).register(any(),any(),any());
    }
    @Test void permissionFailureStopsBeforeAnySubaccountIsCreated() {
        String access=session();doThrow(new WhatsappOnboardingProvider.Failure(false)).when(provider).verifyAuthorization(anyString(),anyString(),anyString(),anyString());
        assertThrows(IllegalArgumentException.class,()->service.complete(access,"code",waba,phoneId));
        verify(provider,never()).createAccount(any());assertNull(row.get().getAccountSid());
    }
    @Test void pendingOrMismatchedSenderIsNeverDeclaredConnected() {
        String access=session();when(provider.register(any(),eq(phone),eq(waba))).thenReturn(new WhatsappOnboardingProvider.Sender(sender,"CREATING",phone,waba));
        assertEquals(false,service.complete(access,"code",waba,phoneId).get("connected"));assertEquals("PENDING",row.get().getState());
        row.get().setUpdatedAt(Instant.now().minusSeconds(10));
        when(provider.refresh(any(),eq(sender))).thenReturn(new WhatsappOnboardingProvider.Sender(sender,"ONLINE","+51922222222",waba));
        assertEquals("REVIEW_REQUIRED",service.sessionStatus(access).get("state"));
    }
    @Test void missingServerConfigurationCannotIssueTicketsOrCreateAccounts() {
        properties.setEncryptionKey("");assertEquals(false,service.information(1L,9L).get("available"));
        assertThrows(IllegalArgumentException.class,this::start);verifyNoInteractions(sessions);verify(provider,never()).createAccount(any());
    }
    @Test void ciphertextIsBoundToTenantAndAccountAndDetectsTampering() {
        String encrypted=cipher.encrypt(1L,account,secret);
        assertEquals(secret,cipher.decrypt(1L,account,encrypted));
        assertThrows(IllegalStateException.class,()->cipher.decrypt(2L,account,encrypted));
        assertThrows(IllegalStateException.class,()->cipher.decrypt(1L,"AC"+"c".repeat(32),encrypted));
        String altered=encrypted.substring(0,encrypted.length()-4)+"AAAA";
        assertThrows(IllegalStateException.class,()->cipher.decrypt(1L,account,altered));
    }
    @Test void provisionedConnectionBecomesAvailableToTheExistingSenderRouter() {
        String access=session();service.complete(access,"meta-code",waba,phoneId);
        when(connections.findByState("ONLINE")).thenReturn(List.of(row.get()));
        var store=new WhatsappBusinessConnectionStore(connections,cipher);
        var actual=new WhatsappBusinessSenderService(settings,store);
        org.springframework.test.util.ReflectionTestUtils.setField(actual,"enabled",true);
        org.springframework.test.util.ReflectionTestUtils.setField(actual,"sharedPhone","+15005550006");
        assertTrue(actual.selection(1L).businessConnected());
        assertEquals(1L,actual.inbound("whatsapp:"+phone).orElseThrow().tenantId());
        assertTrue(actual.forOrigin(2L,phone).isEmpty());
    }
    @Test void retryAfterExplicitSenderRejectionReusesTheExistingSubaccount() {
        String access=session();
        doThrow(new WhatsappOnboardingProvider.Failure(false)).when(provider).register(any(),eq(phone),eq(waba));
        assertEquals("ERROR",service.complete(access,"first-code",waba,phoneId).get("state"));
        assertEquals(account,row.get().getAccountSid());
        doReturn(new WhatsappOnboardingProvider.Sender(sender,"ONLINE",phone,waba)).when(provider).register(any(),eq(phone),eq(waba));
        assertEquals(true,service.complete(access,"new-code",waba,phoneId).get("connected"));
        verify(provider,times(1)).createAccount(1L);
        verify(provider,times(2)).register(any(),eq(phone),eq(waba));
    }
}
