package com.gods.saas.service.impl;
import com.gods.saas.domain.enums.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WhatsappAutomationQueueTest {
 @Test void reminderQueuesOncePerScheduledTimeAndReprogrammingAllowsNewReminder() {
   var notifications=mock(NotificationRepository.class);var deliveries=mock(NotificationDeliveryRepository.class);
   var appointments=mock(AppointmentRepository.class);var settings=mock(TenantSettingsRepository.class);var time=mock(TenantTimeService.class);
   var service=new NotificationServiceImpl(notifications,appointments,time,deliveries,mock(UserTenantRoleRepository.class),mock(CustomerRepository.class),settings,mock(OwnerWhatsappPhoneVerificationService.class));
   var a=Appointment.builder().id(8L).tenant(new Tenant(1L)).customer(Customer.builder().id(3L).build()).fecha(LocalDate.of(2026,11,4)).horaInicio(LocalTime.of(12,0)).estado("RESERVADO").build();
   when(appointments.findForReminder(8L,1L)).thenReturn(Optional.of(a));
   when(time.now(1L)).thenReturn(LocalDateTime.of(2026,11,4,11,0));
   Set<LocalDateTime> scheduled=new HashSet<>();
   when(notifications.existsByTenant_IdAndCustomer_IdAndTypeAndReferenceTypeAndReferenceIdAndAppointmentStart(anyLong(),anyLong(),any(),anyString(),anyLong(),any())).thenAnswer(i -> scheduled.contains(i.getArgument(5)));
   when(notifications.save(any())).thenAnswer(i -> {Notification n=i.getArgument(0);if(n.getAppointmentStart()!=null)scheduled.add(n.getAppointmentStart());return n;});
   service.notifyBookingReminder(a,NotificationType.BOOKING_REMINDER_60);service.notifyBookingReminder(a,NotificationType.BOOKING_REMINDER_60);
   verify(deliveries,times(1)).save(argThat(d -> d.getChannel()==NotificationChannel.WHATSAPP));
   a.setHoraInicio(LocalTime.of(14,0));when(time.now(1L)).thenReturn(LocalDateTime.of(2026,11,4,13,0));
   service.notifyBookingReminder(a,NotificationType.BOOKING_REMINDER_60);
   verify(deliveries,times(2)).save(argThat(d -> d.getChannel()==NotificationChannel.WHATSAPP));
 }
 @Test void dispatchRechecksConsentAndSkipsCancelledDeliveryWithoutCallingProvider() {
   var repository=mock(NotificationDeliveryRepository.class);var subscriptions=mock(SubscriptionRepository.class);
   var sender=mock(WhatsappNotificationSender.class);var policy=mock(TransactionalWhatsappPolicy.class);
   var service=new NotificationDispatchServiceImpl(repository,subscriptions,policy,mock(PushNotificationSender.class),sender);
   var customer=Customer.builder().id(3L).whatsappTransactionalEnabled(false).build();
   var n=Notification.builder().tenant(new Tenant(1L)).customer(customer).type(NotificationType.BOOKING_REMINDER_60).build();
   var delivery=NotificationDelivery.builder().notification(n).channel(NotificationChannel.WHATSAPP).status(NotificationDeliveryStatus.PENDING).build();
   when(repository.findTop100ByChannelAndStatusOrderByCreatedAtAsc(NotificationChannel.WHATSAPP,NotificationDeliveryStatus.PENDING)).thenReturn(List.of(delivery));
   when(policy.current(n,true)).thenReturn(true);service.processPendingWhatsapp();
   assertEquals(NotificationDeliveryStatus.SKIPPED,delivery.getStatus());assertTrue(delivery.getErrorMessage().contains("consentimiento"));verifyNoInteractions(sender);
   customer.setWhatsappTransactionalEnabled(true);when(policy.current(n,true)).thenReturn(false);service.processPendingWhatsapp();
   assertEquals(NotificationDeliveryStatus.SKIPPED,delivery.getStatus());verifyNoInteractions(sender);
 }
 @Test void migrationCanBeAppliedTwice() throws Exception {
   var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
   var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
   jdbc.execute("create table notification(tenant_id bigint,customer_id bigint,type varchar(50),reference_type varchar(50),reference_id bigint)");
   jdbc.execute("create table whatsapp_business_connection(tenant_id bigint primary key)");
   try(var connection=source.getConnection()) {
     var script=new org.springframework.core.io.ClassPathResource("db/manual/20261103_whatsapp_sale_reminders.sql");
     org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
     org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
   }
   jdbc.update("insert into whatsapp_business_connection(tenant_id,sale_receipt_content_sid,reminder60_content_sid) values(1,'HXsale','HXreminder')");
   assertEquals("HXsale",jdbc.queryForObject("select sale_receipt_content_sid from whatsapp_business_connection where tenant_id=1",String.class));
 }

 @Test void saleQueueKeepsFullMessageSnapshotBeyondNotificationPreviewLimit() {
   var notifications=mock(NotificationRepository.class);var deliveries=mock(NotificationDeliveryRepository.class);
   var service=new NotificationServiceImpl(notifications,mock(AppointmentRepository.class),mock(TenantTimeService.class),deliveries,mock(UserTenantRoleRepository.class),mock(CustomerRepository.class),mock(TenantSettingsRepository.class),mock(OwnerWhatsappPhoneVerificationService.class));
   var snapshot=PostSaleWhatsappMessageFactory.compose("Ana","Barberia Gods",270,150,420,"https://play.google.com/store/apps/details?id=com.gods.barberia","GODS","https://www.supergodsapp.com/reservar/GODS","\n\nTambien puedes reservar por WhatsApp con nuestro asistente. Abre este enlace y envia: RESERVAR\nhttps://wa.me/51958062847?text=RESERVAR");
   var sale=Sale.builder().id(12L).tenant(new Tenant(1L)).customer(Customer.builder().id(3L).build()).paymentValidationStatus("APPROVED").whatsappReceiptSnapshot(snapshot.stored()).build();
   when(notifications.save(any())).thenAnswer(i -> i.getArgument(0));
   service.notifySaleReceipt(sale,snapshot.message(),false);
   var captured=org.mockito.ArgumentCaptor.forClass(Notification.class);verify(notifications,times(2)).save(captured.capture());
   var notification=captured.getValue();assertEquals(500,notification.getMessage().length());
   assertEquals(snapshot,PostSaleWhatsappMessageFactory.restore(notification.getWhatsappTemplateSnapshot()));
   assertTrue(snapshot.message().length()>notification.getMessage().length());
 }
 @Test void postSaleInvitationRequiresBothMarketingAndTransactionalConsent() {
   var repository=mock(NotificationDeliveryRepository.class);var subscriptions=mock(SubscriptionRepository.class);
   var sender=mock(WhatsappNotificationSender.class);var policy=mock(TransactionalWhatsappPolicy.class);
   var service=new NotificationDispatchServiceImpl(repository,subscriptions,policy,mock(PushNotificationSender.class),sender);
   var customer=Customer.builder().id(3L).whatsappTransactionalEnabled(true).whatsappMarketingEnabled(false).build();
   var n=Notification.builder().tenant(new Tenant(1L)).customer(customer).type(NotificationType.SALE_RECEIPT).whatsappTemplateSnapshot(Map.of("marketing",true)).build();
   var delivery=NotificationDelivery.builder().notification(n).channel(NotificationChannel.WHATSAPP).status(NotificationDeliveryStatus.PENDING).build();
   when(repository.findTop100ByChannelAndStatusOrderByCreatedAtAsc(NotificationChannel.WHATSAPP,NotificationDeliveryStatus.PENDING)).thenReturn(List.of(delivery));
   when(policy.current(n,true)).thenReturn(true);service.processPendingWhatsapp();assertEquals(NotificationDeliveryStatus.SKIPPED,delivery.getStatus());verifyNoInteractions(sender);
   customer.setWhatsappMarketingEnabled(true);customer.setWhatsappTransactionalEnabled(false);service.processPendingWhatsapp();assertEquals(NotificationDeliveryStatus.SKIPPED,delivery.getStatus());verifyNoInteractions(sender);
 }
 @Test void fullMessageMigrationIsRepeatable() throws Exception {
   var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
   var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
   jdbc.execute("create table sale(sale_id bigint primary key)");jdbc.execute("create table notification(notification_id bigint primary key)");jdbc.execute("create table whatsapp_business_connection(tenant_id bigint primary key)");
   try(var connection=source.getConnection()) {
     var script=new org.springframework.core.io.ClassPathResource("db/manual/20261103_whatsapp_post_sale_parity.sql");
     org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
   }
   jdbc.update("insert into whatsapp_business_connection(tenant_id,sale_receipt_content_sids) values(1,'{}')");
   assertEquals(1,jdbc.queryForObject("select count(*) from whatsapp_business_connection where sale_receipt_content_sids is not null",Integer.class));
 }
}
