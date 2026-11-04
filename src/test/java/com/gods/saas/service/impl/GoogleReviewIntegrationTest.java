package com.gods.saas.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.SubscriptionService;
import com.gods.saas.web.controller.PublicGoogleReviewController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class GoogleReviewIntegrationTest {
 static final String URL="https://g.page/r/ABC123/review";
 Branch branch(long tenant,long id) {var b=Branch.builder().id(id).tenant(new Tenant(tenant)).nombre("Principal").activo(true).googleReviewUrl(URL).build();b.getTenant().setNombre("Salon");return b;}
 @ParameterizedTest @ValueSource(strings={"https://g.page/r/ABC123/review","https://g.page/barberiagods/review?rc","https://search.google.com/local/writereview?placeid=ChIJ123","https://maps.app.goo.gl/AbC123","https://www.google.com/maps/place/Salon/"})
 void acceptsOfficialGoogleReviewDestinations(String url){assertEquals(url,GoogleReviewLinks.normalize(url));}
 @ParameterizedTest @ValueSource(strings={"http://g.page/r/ABC/review","javascript:alert(1)","https://g.page.evil.test/r/ABC/review","https://g.page@evil.test/r/ABC/review","https://evil@g.page/r/ABC/review","https://www.google.com/url?q=https://evil.test","https://search.google.com/local/writereview","https://g.page:8443/r/ABC/review","https://evil.test/review","https://g.page/r/ABC/rev\niew"})
 void rejectsUnsafeOrNonReviewDestinations(String url){assertNull(GoogleReviewLinks.safe(url));}
 @Test void ownerCanUpdateLinkAndOlderClientsCannotClearIt() throws Exception {
   var branches=mock(BranchRepository.class);var b=branch(1,10);b.setGoogleReviewWhatsappEnabled(true);
   var service=new OwnerBranchService(branches,mock(ClientBusinessFollowRepository.class),mock(AffiliatedDiscoveryEventRepository.class),mock(TenantRepository.class),mock(SubscriptionService.class),mock(CloudinaryStorageService.class));
   when(branches.findByIdAndTenant_Id(10L,1L)).thenReturn(Optional.of(b));when(branches.save(b)).thenReturn(b);
   var mapper=new ObjectMapper();var legacy=mapper.readValue("{\"nombre\":\"Principal\"}",OwnerBranchUpsertRequest.class);
   var response=service.updateBranch(1L,10L,legacy);assertEquals(URL,response.googleReviewUrl());assertTrue(response.googleReviewWhatsappEnabled());
   var clear=mapper.readValue("{\"nombre\":\"Principal\",\"googleReviewUrl\":\"\",\"googleReviewWhatsappEnabled\":false}",OwnerBranchUpsertRequest.class);
   response=service.updateBranch(1L,10L,clear);assertNull(response.googleReviewUrl());assertFalse(response.googleReviewWhatsappEnabled());
   assertThrows(RuntimeException.class,()->service.updateBranch(2L,10L,legacy));
 }
 @ParameterizedTest @ValueSource(ints={1,2,3,4,5})
 void sameGoogleLinkForEveryInternalRating(int rating) {
   var appointments=mock(AppointmentRepository.class);var sales=mock(SaleRepository.class);var reviews=mock(VerifiedBusinessReviewRepository.class);
   var b=branch(1,10);var customer=Customer.builder().id(3L).nombres("Ana").build();var sale=Sale.builder().id(8L).tenant(b.getTenant()).branch(b).customer(customer).paymentValidationStatus("APPROVED").build();
   when(sales.findByIdAndTenant_IdAndCustomer_Id(8L,1L,3L)).thenReturn(Optional.of(sale));when(reviews.save(any())).thenAnswer(i->i.getArgument(0));
   var response=new VerifiedBusinessReviewService(appointments,sales,reviews).create(1L,3L,new CreateVerifiedReviewRequest(null,8L,rating,"Mi experiencia"));
   assertEquals(URL,response.googleReviewUrl());assertEquals(rating,response.rating());
 }
 @Test void publicInvitationHasOnlyNamesAndLinkAndSkipsInactiveBusinesses() {
   var branches=mock(BranchRepository.class);var b=branch(1,10);when(branches.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(b));
   var controller=new PublicGoogleReviewController(branches);var response=controller.invitation(10L);
   assertEquals(Set.of("businessName","branchName","googleReviewUrl"),response.getBody().keySet());assertEquals(URL,response.getBody().get("googleReviewUrl"));
   b.getTenant().setActive(false);assertThrows(RuntimeException.class,()->controller.invitation(10L));
 }
 @Test void onlyTheConfiguredSaleBranchCanAddGoogleAndSnapshotDoesNotChangeLater() {
   var settings=mock(TenantSettingsRepository.class);var movements=mock(LoyaltyMovementRepository.class);var accounts=mock(LoyaltyAccountRepository.class);var invitation=mock(PostSaleWhatsappAssistantInvitation.class);
   var factory=new PostSaleWhatsappMessageFactory(settings,movements,accounts,invitation);var b=branch(1,10);var sale=Sale.builder().id(8L).tenant(b.getTenant()).branch(b).customer(Customer.builder().id(3L).nombres("Ana").build()).build();
   assertFalse(factory.forSale(sale,0).variant().endsWith("_google"));b.setGoogleReviewWhatsappEnabled(true);
   var snapshot=factory.forSale(sale,0);assertTrue(snapshot.variant().endsWith("_google"));assertTrue(snapshot.message().contains(URL));assertTrue(snapshot.marketing());
   String rendered=snapshot.template();for(var e:snapshot.variables().entrySet())rendered=rendered.replace("{{"+e.getKey()+"}}",e.getValue());assertEquals(snapshot.message(),rendered);
   sale.setWhatsappReceiptSnapshot(snapshot.stored());b.setGoogleReviewUrl("https://g.page/r/OTHER/review");assertEquals(snapshot,factory.forSale(sale,0));
   sale.setWhatsappReceiptSnapshot(null);b.setTenant(new Tenant(2L));assertFalse(factory.forSale(sale,0).variant().endsWith("_google"));
 }

 @Test void everyGoogleTemplateMatchesManualTextAndCannotUseAnOlderTemplateSid() throws Exception {
   for(boolean app:List.of(false,true))for(boolean code:app?List.of(false,true):List.of(false))for(boolean booking:List.of(false,true))for(boolean bot:List.of(false,true)) {
     String assistant=bot?"\n\nTambien puedes reservar por WhatsApp con nuestro asistente. Abre este enlace y envia: RESERVAR\nhttps://wa.me/51958062847?text=RESERVAR":"";
     var snapshot=PostSaleWhatsappMessageFactory.compose("Ana","Barberia Gods",270,150,420,app?"https://play.google.com/store/apps/details?id=com.gods.barberia":"",code?"GODS":"",booking?"https://www.supergodsapp.com/reservar/GODS":"",assistant,URL);
     assertEquals(java.nio.file.Files.readString(java.nio.file.Path.of("docs/whatsapp-postventa-templates",snapshot.variant()+".txt")).stripTrailing(),snapshot.template());
   }
   var snapshot=PostSaleWhatsappMessageFactory.compose("Ana","Barberia Gods",270,150,420,"","","","",URL);
   var n=Notification.builder().tenant(new Tenant(1L)).customer(Customer.builder().id(3L).build()).type(com.gods.saas.domain.enums.NotificationType.SALE_RECEIPT).whatsappTemplateSnapshot(snapshot.stored()).build();
   var policy=mock(TransactionalWhatsappPolicy.class);when(policy.current(n,true)).thenReturn(true);
   var factory=new CustomerWhatsappTemplateFactory(mock(AppointmentRepository.class),policy,new ObjectMapper());
   var wrong=new WhatsappBusinessSenderService.Connection(1L,"+51911111111","AC"+"a".repeat(32),"b".repeat(32),null,null,"HX"+"c".repeat(32),null);
   assertThrows(IllegalStateException.class,()->factory.from(n,wrong));
   var own=new WhatsappBusinessSenderService.Connection(1L,wrong.phone(),wrong.accountSid(),wrong.authToken(),null,null,null,null,Map.of("points_google","HX"+"d".repeat(32)));
   var payload=factory.from(n,own).orElseThrow();assertEquals("HX"+"d".repeat(32),payload.contentSid());assertEquals(URL,new ObjectMapper().readTree(payload.variables()).get("6").asText());
 }
}
