package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.SubscriptionService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsappCustomerOnboardingTest {
    final CustomerRepository customers=mock(CustomerRepository.class);
    final TenantRepository tenants=mock(TenantRepository.class);
    final SubscriptionService subscriptions=mock(SubscriptionService.class);
    final WhatsappAssistantPolicy policy=mock(WhatsappAssistantPolicy.class);
    final WhatsappAssistantUsageService usage=mock(WhatsappAssistantUsageService.class);
    final TenantTimeService time=mock(TenantTimeService.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ObjectMapper mapper=new ObjectMapper();
    final InternationalPhoneService phones=mock(InternationalPhoneService.class);
    final WhatsappCustomerOnboarding service=new WhatsappCustomerOnboarding(customers,tenants,subscriptions,policy,usage,time,jdbc,mapper,phones);
    final Instant now=Instant.parse("2026-10-28T18:00:00Z");
    final String phone="+51999999999";
    Tenant tenant;
    ObjectNode state;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"tenantIds","2,3");
        ReflectionTestUtils.setField(service,"signedInbound",true);
        ReflectionTestUtils.setField(service,"entityManager",mock(jakarta.persistence.EntityManager.class));
        tenant=new Tenant(2L); tenant.setNombre("Barberia Indystry");tenant.setCodigo("INDYSTRY");tenant.setActive(true);
        when(tenants.findAllById(any())).thenReturn(List.of(tenant));
        when(tenants.findById(2L)).thenReturn(Optional.of(tenant));
        Subscription sub=new Subscription();sub.setPlan("PRO");
        when(subscriptions.getCurrentSubscriptionOrThrow(anyLong())).thenReturn(sub);
        when(policy.monthlyTurns("PRO")).thenReturn(600);
        when(time.today(anyLong())).thenReturn(LocalDate.of(2026,10,28));
        when(usage.reserve(anyLong(),anyString(),anyInt())).thenReturn(true);
        state=mapper.createObjectNode();
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(inv->{
            String sql=inv.getArgument(0); Object[] args=Arrays.copyOfRange(inv.getArguments(),1,inv.getArguments().length);
            if(sql.contains("draft=?")) state=(ObjectNode)mapper.readTree(args[sql.contains("tenant_id=?")?1:0].toString());
            return 1;
        });
    }
    String reply(String text) throws Exception {return service.reply(phone,text,state,now);}
    void consent() throws Exception {reply("quiero reservar en INDYSTRY");reply("Ana Perez");}
    @Test void naturalBusinessSelectionAsksNameThenExplicitConsent() throws Exception {
        assertTrue(reply("Quiero reservar en INDYSTRY").contains("nombre"));
        assertTrue(reply("Ana Pérez").contains("ACEPTO"));
        reply("si"); verify(customers,never()).saveAndFlush(any());
        assertTrue(reply("ACEPTO").contains("1. Reservar"));
        var capture=org.mockito.ArgumentCaptor.forClass(Customer.class);verify(customers).saveAndFlush(capture.capture());
        Customer saved=capture.getValue();assertEquals(2L,saved.getTenant().getId());assertEquals(phone,saved.getTelefono());
        assertTrue(saved.isPhoneVerified());assertFalse(saved.getWhatsappMarketingEnabled());assertFalse(saved.getAppActivated());
        assertTrue(saved.getCustomerNotes().contains("2026-10-28"));assertNull(saved.getPasswordHash());
    }
    @Test void declineNeverCreatesCustomer() throws Exception {consent();assertTrue(reply("NO").contains("cancelada"));verify(customers,never()).saveAndFlush(any());}
    @Test void existingUnverifiedAndNationalAliasesCannotBeClaimed() throws Exception {
        Customer old=new Customer();old.setTelefono("999999999");old.setPhoneVerified(false);
        when(customers.findPhoneCandidates(anyLong(),anyString(),anyString(),anyString(),anyString())).thenReturn(List.of(old));
        assertTrue(reply("INDYSTRY").contains("Verifica"));verify(customers,never()).saveAndFlush(any());
    }
    @Test void repeatedConsentAfterActivationDoesNotRegisterAgain() throws Exception {
        consent();reply("ACEPTO");reply("ACEPTO");verify(customers,times(1)).saveAndFlush(any());
    }
    @Test void signedWebhookRequiredBeforeCreatingVerifiedCustomer() throws Exception {
        consent();ReflectionTestUtils.setField(service,"signedInbound",false);
        assertTrue(reply("ACEPTO").contains("firma"));verify(customers,never()).saveAndFlush(any());
    }
    @Test void tenantWhitelistAndSubscriptionRecheckedAtConsent() throws Exception {
        consent();ReflectionTestUtils.setField(service,"tenantIds","3");reply("ACEPTO");verify(customers,never()).saveAndFlush(any());
    }
    @Test void ambiguousBusinessRequiresExplicitSelection() throws Exception {
        Tenant second=new Tenant(3L);second.setNombre("Barberia Indystry Norte");second.setCodigo("NORTE");second.setActive(true);
        when(tenants.findAllById(any())).thenReturn(List.of(tenant,second));
        assertTrue(reply("barberia").contains("varias"));assertEquals("DISCOVER",state.path("_assistant").path("stage").asText());
        assertTrue(reply("1").contains("nombre"));verify(customers,never()).saveAndFlush(any());
    }
    @Test void customerAppearingBeforeConsentPreventsDuplicate() throws Exception {
        consent();Customer old=new Customer();old.setTelefono(phone);old.setPhoneVerified(false);
        when(customers.findPhoneCandidates(anyLong(),anyString(),anyString(),anyString(),anyString())).thenReturn(List.of(old));
        assertTrue(reply("ACEPTO").contains("Verifica"));verify(customers,never()).saveAndFlush(any());
    }
    @Test void quotaExhaustedDoesNotCreateOrActivate() throws Exception {
        consent();when(usage.reserve(anyLong(),anyString(),anyInt())).thenReturn(false);
        assertTrue(reply("ACEPTO").contains("limite"));verify(customers,never()).saveAndFlush(any());
    }
    @Test void optedOutOrInactiveCustomerCannotBeReactivatedByChat() throws Exception {
        Customer old=new Customer();old.setTelefono(phone);old.setPhoneVerified(true);old.setActivo(false);old.setWhatsappTransactionalEnabled(true);
        when(customers.findPhoneCandidates(anyLong(),anyString(),anyString(),anyString(),anyString())).thenReturn(List.of(old));
        assertTrue(reply("INDYSTRY").contains("Verifica"));verify(customers,never()).saveAndFlush(any());
    }

    private Customer existingCustomer(String storedPhone) {
        Customer client=Customer.builder().id(71L).tenant(tenant).telefono(storedPhone).nombres("Cliente existente")
                .phoneVerified(false).puntosDisponibles(150).passwordHash("existing-password-hash").build();
        when(customers.findPhoneCandidates(anyLong(),anyString(),anyString(),anyString(),anyString())).thenReturn(List.of(client));
        when(customers.findForWhatsappRecovery(71L,2L)).thenReturn(Optional.of(client));
        return client;
    }
    @Test void existingCustomerCanVerifyPhoneWithExplicitConsentWithoutCreatingDuplicateOrChangingAccount() throws Exception {
        Customer client=existingCustomer(phone);
        assertTrue(reply("INDYSTRY").contains("Responde ACEPTO"));
        assertFalse(client.isPhoneVerified());verify(customers,never()).saveAndFlush(any());
        assertTrue(reply("ACEPTO").contains("1. Reservar"));
        assertTrue(client.isPhoneVerified());assertTrue(client.getWhatsappTransactionalEnabled());assertFalse(client.getWhatsappMarketingEnabled());
        assertEquals(71L,client.getId());assertEquals(150,client.getPuntosDisponibles());assertEquals("existing-password-hash",client.getPasswordHash());
        verify(customers).saveAndFlush(same(client));assertTrue(client.getCustomerNotes().contains("Webhook Twilio firmado"));
    }
    @Test void nationalPhoneAliasIsNormalizedOnlyWhenItMatchesIncomingNumberInBusinessRegion() throws Exception {
        Customer client=existingCustomer("999999999");
        when(phones.normalize(tenant,"999999999")).thenReturn(new InternationalPhoneService.NormalizedPhone(phone,"51999999999","999999999",Set.of("999999999"),"PE","PE"));
        assertTrue(reply("INDYSTRY").contains("ACEPTO"));reply("ACEPTO");assertEquals(phone,client.getTelefono());
    }
    @Test void aliasFromAnotherCountryCannotClaimExistingCustomer() throws Exception {
        existingCustomer("999999999");
        when(phones.normalize(tenant,"999999999")).thenReturn(new InternationalPhoneService.NormalizedPhone("+34999999999","34999999999","999999999",Set.of("999999999"),"ES","ES"));
        assertFalse(reply("INDYSTRY").contains("Responde ACEPTO"));verify(customers,never()).saveAndFlush(any());
    }
    @Test void explicitConsentCanRestoreTransactionalNoticesButNeverAdvertising() throws Exception {
        Customer client=existingCustomer(phone);client.setPhoneVerified(true);client.setWhatsappOptedOutAt(LocalDateTime.now());client.setWhatsappTransactionalEnabled(false);client.setWhatsappMarketingEnabled(true);
        assertTrue(reply("INDYSTRY").contains("ACEPTO"));reply("ACEPTO");
        assertNull(client.getWhatsappOptedOutAt());assertTrue(client.getWhatsappTransactionalEnabled());assertFalse(client.getWhatsappMarketingEnabled());
    }
    @Test void rejectionNeverVerifiesExistingCustomer() throws Exception {
        Customer client=existingCustomer(phone);reply("INDYSTRY");reply("NO");assertFalse(client.isPhoneVerified());verify(customers,never()).saveAndFlush(any());
    }
    @Test void recoveryCannotProceedIfSignatureValidationIsDisabledOrTenantRemoved() throws Exception {
        Customer client=existingCustomer(phone);reply("INDYSTRY");ReflectionTestUtils.setField(service,"signedInbound",false);
        reply("ACEPTO");assertFalse(client.isPhoneVerified());verify(customers,never()).saveAndFlush(any());
        ReflectionTestUtils.setField(service,"signedInbound",true);ReflectionTestUtils.setField(service,"tenantIds","3");reply("ACEPTO");verify(customers,never()).saveAndFlush(any());
    }
    @Test void duplicateCustomerRecordsOrInactiveAccountsCannotBeClaimed() throws Exception {
        Customer client=existingCustomer(phone);Customer other=Customer.builder().id(72L).tenant(tenant).telefono(phone).build();
        when(customers.findPhoneCandidates(anyLong(),anyString(),anyString(),anyString(),anyString())).thenReturn(List.of(client,other));
        assertFalse(reply("INDYSTRY").contains("Responde ACEPTO"));verify(customers,never()).saveAndFlush(any());
    }
    @Test void pendingPhoneChangeIsRecheckedBeforeAnyUpdate() throws Exception {
        Customer client=existingCustomer(phone);reply("INDYSTRY");client.setPhonePendiente("+51988888888");client.setPhonePendienteVerificacion(true);
        assertFalse(reply("ACEPTO").contains("1. Reservar"));assertFalse(client.isPhoneVerified());verify(customers,never()).saveAndFlush(any());
    }
}
