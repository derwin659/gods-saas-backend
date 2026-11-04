package com.gods.saas.service.impl;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WhatsappAutomationTransportTest {
 @Test void saleUsesSubaccountCredentialsFromAndApprovedTemplateWithoutSharedSenderPool() throws Exception {
   var settings=mock(TenantSettingsRepository.class);var connections=mock(WhatsappBusinessSenderService.class);
   var recipients=mock(WhatsappRecipientResolver.class);var templates=mock(CustomerWhatsappTemplateFactory.class);
   var sender=new WhatsappNotificationSenderTwilioImpl(settings,connections,recipients,mock(OwnerBookingWhatsappPayloadFactory.class),templates);
   var own=new WhatsappBusinessSenderService.Connection(1L,"+51911111111","AC"+"a".repeat(32),"b".repeat(32),null,null,"HX"+"c".repeat(32),"HX"+"d".repeat(32));
   var n=Notification.builder().id(5L).tenant(new Tenant(1L)).customer(Customer.builder().id(3L).build()).type(NotificationType.SALE_RECEIPT).message("Venta aprobada").build();
   ReflectionTestUtils.setField(sender,"enabled",true);
   ReflectionTestUtils.setField(sender,"defaultAccountSid","AC"+"f".repeat(32));
   ReflectionTestUtils.setField(sender,"defaultAuthToken","e".repeat(32));
   ReflectionTestUtils.setField(sender,"defaultMessagingServiceSid","MG"+"f".repeat(32));
   when(connections.selection(eq(1L),anyMap())).thenReturn(new WhatsappBusinessSenderService.Selection("BUSINESS",own.phone(),"CONNECTED","BUSINESS",own.phone()));
   when(connections.connection(1L)).thenReturn(Optional.of(own));
   when(recipients.resolve(n)).thenReturn(new WhatsappRecipientResolver.Recipient("51922222222",3L,null));
   when(templates.from(n,own)).thenReturn(Optional.of(new CustomerWhatsappTemplateFactory.Payload(own.saleReceiptContentSid(),"{\"1\":\"Ana\"}")));
   var client=mock(HttpClient.class);var builder=mock(HttpClient.Builder.class,RETURNS_SELF);
   when(builder.build()).thenReturn(client);
   HttpResponse<String> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(201);when(response.body()).thenReturn("{\"sid\":\"SM123\"}");
   when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
   try(var http=mockStatic(HttpClient.class)) {
     http.when(HttpClient::newBuilder).thenReturn(builder);assertEquals("SM123",sender.send(n));
   }
   var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(request.capture(),any(HttpResponse.BodyHandler.class));
   assertTrue(request.getValue().uri().getPath().contains(own.accountSid()));
   assertEquals("Basic "+Base64.getEncoder().encodeToString((own.accountSid()+":"+own.authToken()).getBytes(StandardCharsets.UTF_8)),request.getValue().headers().firstValue("Authorization").orElseThrow());
   var bytes=new ByteArrayOutputStream();
   request.getValue().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
      public void onSubscribe(Flow.Subscription s) {s.request(Long.MAX_VALUE);}
      public void onNext(ByteBuffer b) {var data=new byte[b.remaining()];b.get(data);bytes.writeBytes(data);}
      public void onError(Throwable t) {throw new AssertionError(t);}
      public void onComplete() {}
   });
   String form=URLDecoder.decode(bytes.toString(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
   assertTrue(form.contains("From=whatsapp:"+own.phone()));assertTrue(form.contains("ContentSid="+own.saleReceiptContentSid()));
   assertFalse(form.contains("MessagingServiceSid="));assertFalse(form.contains("Body="));
 }
}
