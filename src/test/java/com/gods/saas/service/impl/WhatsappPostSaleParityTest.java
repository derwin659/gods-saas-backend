package com.gods.saas.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WhatsappPostSaleParityTest {
 final TenantSettingsRepository settings=mock(TenantSettingsRepository.class);
 final LoyaltyMovementRepository movements=mock(LoyaltyMovementRepository.class);
 final LoyaltyAccountRepository accounts=mock(LoyaltyAccountRepository.class);
 final PostSaleWhatsappAssistantInvitation invitation=mock(PostSaleWhatsappAssistantInvitation.class);
 final PostSaleWhatsappMessageFactory messages=new PostSaleWhatsappMessageFactory(settings,movements,accounts,invitation);
 static final String ASSISTANT="\n\nTambien puedes reservar por WhatsApp con nuestro asistente. Abre este enlace y envia: RESERVAR\nhttps://wa.me/51958062847?text=RESERVAR";
 Sale sale() {
   var tenant=new Tenant(1L);tenant.setNombre("Barberia Gods");tenant.setCodigo("GODS");
   return Sale.builder().id(12L).tenant(tenant).customer(Customer.builder().id(3L).nombres("Ana Maria").build()).paymentValidationStatus("APPROVED").build();
 }
 @Test void exactManualTextEqualsApprovedTemplateRenderingForEveryOptionalCombination() throws Exception {
   for(boolean app:List.of(false,true)) for(boolean code:app?List.of(false,true):List.of(false)) for(boolean booking:List.of(false,true)) for(boolean bot:List.of(false,true)) {
     var snapshot=PostSaleWhatsappMessageFactory.compose("Ana","Barberia Gods",270,150,420,
       app?"https://play.google.com/store/apps/details?id=com.gods.barberia":"",code?"GODS":"",booking?"https://www.supergodsapp.com/reservar/GODS":"",bot?ASSISTANT:"");
     String rendered=snapshot.template();
     for(var entry:snapshot.variables().entrySet()) { rendered=rendered.replace("{{"+entry.getKey()+"}}",entry.getValue());assertFalse(entry.getValue().contains("\n")); }
     assertEquals(snapshot.message(),rendered);
     assertTrue(snapshot.message().contains("Puntos antes de esta compra: 270.\nGanaste +150 puntos por tu compra.\nAhora tienes 420 puntos disponibles."));
     assertEquals(app,snapshot.message().contains("Descarga la app"));assertEquals(booking,snapshot.message().contains("/reservar/"));assertEquals(bot,snapshot.message().contains("envia: RESERVAR"));
     assertEquals(app||booking||bot,snapshot.marketing());
     assertEquals(Files.readString(Path.of("docs/whatsapp-postventa-templates",snapshot.variant()+".txt")).stripTrailing(),snapshot.template());
     assertEquals(snapshot,PostSaleWhatsappMessageFactory.restore(new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(snapshot.stored()),Map.class)));
   }
 }
 @Test void usesSaleLedgerNotLaterCustomerBalanceAndFreezesFullLinks() {
   var sale=sale();var movement=LoyaltyMovement.builder().puntos(150).saldoResultante(420).build();
   when(movements.findFirstByTenantIdAndCustomerIdAndOrigenAndReferenciaIdAndTipoOrderByIdAsc(1L,3L,"SALE",12L,"EARN")).thenReturn(Optional.of(movement));
   when(invitation.build(1L)).thenReturn(ASSISTANT);
   var snapshot=messages.forSale(sale,999);sale.setWhatsappReceiptSnapshot(snapshot.stored());
   assertEquals("Ana",snapshot.variables().get("1"));assertEquals("270",snapshot.variables().get("3"));assertEquals("150",snapshot.variables().get("4"));assertEquals("420",snapshot.variables().get("5"));
   assertEquals(PostSaleWhatsappMessageFactory.FULL_VARIANT,snapshot.variant());assertTrue(snapshot.message().length()>500);
   verifyNoInteractions(accounts);clearInvocations(settings,movements,accounts,invitation);
   var frozen=messages.forSale(sale,0);assertEquals(snapshot,frozen);verifyNoInteractions(settings,movements,accounts,invitation);
 }
 @Test void zeroPointsAndNoLinksStillHaveIdenticalBeforeAndAfter() {
   var sale=sale();var config=new TenantSettings();config.setScheduleConfig(Map.of(OwnerWhatsappSettingsService.INCLUDE_APP_DOWNLOAD_LINK_KEY,false,OwnerWhatsappSettingsService.INCLUDE_BOOKING_LINK_KEY,false));
   when(settings.findByTenant_Id(1L)).thenReturn(Optional.of(config));
   when(accounts.findByTenant_IdAndCustomer_Id(1L,3L)).thenReturn(Optional.of(LoyaltyAccount.builder().puntosDisponibles(420).build()));
   var snapshot=messages.forSale(sale,0);
   assertEquals("points",snapshot.variant());assertEquals("420",snapshot.variables().get("3"));assertEquals("0",snapshot.variables().get("4"));assertEquals("420",snapshot.variables().get("5"));verifyNoInteractions(invitation);
 }
 @Test void templateTransportUsesFrozenValuesAndNeverFallsBackToAnotherVariant() throws Exception {
   var snapshot=PostSaleWhatsappMessageFactory.compose("Ana","Barberia Gods",270,150,420,"","","","");
   var n=Notification.builder().tenant(new Tenant(1L)).customer(Customer.builder().id(3L).build()).type(NotificationType.SALE_RECEIPT).whatsappTemplateSnapshot(snapshot.stored()).build();
   var policy=mock(TransactionalWhatsappPolicy.class);when(policy.current(n,true)).thenReturn(true);
   var factory=new CustomerWhatsappTemplateFactory(mock(AppointmentRepository.class),policy,new ObjectMapper());
   var own=new WhatsappBusinessSenderService.Connection(1L,"+51911111111","AC"+"a".repeat(32),"b".repeat(32),null,null,"HX"+"c".repeat(32),null,Map.of("points","HX"+"d".repeat(32)));
   var payload=factory.from(n,own).orElseThrow();assertEquals("HX"+"d".repeat(32),payload.contentSid());
   var vars=new ObjectMapper().readTree(payload.variables());assertEquals("270",vars.get("3").asText());assertEquals("150",vars.get("4").asText());assertEquals("420",vars.get("5").asText());
   var wrong=new WhatsappBusinessSenderService.Connection(1L,own.phone(),own.accountSid(),own.authToken(),null,null,own.saleReceiptContentSid(),null);
   assertThrows(IllegalStateException.class,()->factory.from(n,wrong));n.setWhatsappTemplateSnapshot(null);assertThrows(IllegalStateException.class,()->factory.from(n,own));
 }
}
