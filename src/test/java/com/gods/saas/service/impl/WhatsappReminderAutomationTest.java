package com.gods.saas.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.enums.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WhatsappReminderAutomationTest {
 final AppointmentRepository appointments=mock(AppointmentRepository.class);
 final SaleRepository sales=mock(SaleRepository.class);
 final TenantSettingsRepository settings=mock(TenantSettingsRepository.class);
 final TenantTimeService time=mock(TenantTimeService.class);
 final TransactionalWhatsappPolicy policy=new TransactionalWhatsappPolicy(appointments,sales,settings,time,org.mockito.Mockito.mock(com.gods.saas.domain.repository.BookingWaitlistRepository.class));
 Appointment a; Notification n;
 @BeforeEach void setup() {
   var tenant=new Tenant(1L); tenant.setNombre("Salon");
   var customer=new Customer();customer.setId(3L);customer.setNombres("Ana");
   var service=new ServiceEntity();service.setNombre("Corte");
   a=Appointment.builder().id(8L).tenant(tenant).customer(customer).service(service).fecha(LocalDate.of(2026,11,4)).horaInicio(LocalTime.of(0,30)).estado("RESERVADO").build();
   n=Notification.builder().tenant(tenant).customer(customer).type(NotificationType.BOOKING_REMINDER_60).referenceType("APPOINTMENT").referenceId(8L).appointmentStart(LocalDateTime.of(a.getFecha(),a.getHoraInicio())).build();
   when(appointments.findByIdAndTenant_Id(8L,1L)).thenReturn(Optional.of(a));
   when(time.now(1L)).thenReturn(LocalDateTime.of(2026,11,3,23,30));
 }
 @Test void reminderWorksAcrossLocalMidnightAndNeverSendsWhatsappAt30Minutes() {
   assertTrue(policy.current(n,true));n.setType(NotificationType.BOOKING_REMINDER_30);assertFalse(policy.current(n,true));
 }
 @Test void cancelledCompletedAndPendingDepositAreNotSent() {
   for(String status:List.of("CANCELADO","COMPLETADO","ATENDIDO")) {a.setEstado(status);assertFalse(policy.current(n,true));}
   a.setEstado("RESERVADO");a.setDepositRequired(true);a.setDepositStatus("PENDING_VALIDATION");assertFalse(policy.current(n,true));
 }
 @Test void rescheduledOrLateReminderIsSkipped() {
   a.setHoraInicio(LocalTime.of(1,30));assertFalse(policy.current(n,true));
   a.setHoraInicio(LocalTime.of(0,30));when(time.now(1L)).thenReturn(LocalDateTime.of(2026,11,4,0,20));assertFalse(policy.current(n,true));
 }
 @Test void disabledReminderAndDifferentCustomerAreSkipped() {
   var config=new TenantSettings();config.setScheduleConfig(Map.of(OwnerWhatsappSettingsService.REMINDER_60_ENABLED_KEY,false));
   when(settings.findByTenant_Id(1L)).thenReturn(Optional.of(config));assertFalse(policy.current(n,true));
   when(settings.findByTenant_Id(1L)).thenReturn(Optional.empty());n.setCustomer(Customer.builder().id(99L).build());assertFalse(policy.current(n,true));
 }
 @Test void schedulerUsesTenantTimeAndFindsTomorrowAppointments() {
   var notify=mock(NotificationService.class);
   when(appointments.findByFechaBetween(any(),any())).thenReturn(List.of(a));
   new BookingReminderScheduler(appointments,notify,settings,time).processReminders();
   verify(notify).notifyBookingReminder(a,NotificationType.BOOKING_REMINDER_60);
   verify(notify,never()).notifyBookingReminder(a,NotificationType.BOOKING_REMINDER_30);
 }
 @Test void approvedTemplateBelongsToOwnConnectionAndUsesCurrentAppointmentFields() throws Exception {
   var factory=new CustomerWhatsappTemplateFactory(appointments,policy,new ObjectMapper());
   var own=new WhatsappBusinessSenderService.Connection(1L,"+51911111111","AC"+"a".repeat(32),"b".repeat(32),null,null,"HX"+"c".repeat(32),"HX"+"d".repeat(32));
   var payload=factory.from(n,own).orElseThrow();
   assertEquals(own.reminder60ContentSid(),payload.contentSid());
   var vars=new ObjectMapper().readTree(payload.variables());assertEquals("00:30",vars.get("5").asText());assertEquals("04/11/2026",vars.get("4").asText());
   assertThrows(IllegalStateException.class,()->factory.from(n,null));
   var missing=new WhatsappBusinessSenderService.Connection(1L,own.phone(),own.accountSid(),own.authToken(),null,null);
   assertThrows(IllegalStateException.class,()->factory.from(n,missing));
 }
 @Test void saleTemplateCannotSendPendingOrRejectedSale() {
   n.setType(NotificationType.SALE_RECEIPT);n.setReferenceId(12L);
   var sale=Sale.builder().id(12L).tenant(n.getTenant()).customer(n.getCustomer()).total(new BigDecimal("35.00")).paymentValidationStatus("PENDING_VALIDATION").build();
   when(sales.findByIdAndTenant_Id(12L,1L)).thenReturn(Optional.of(sale));assertFalse(policy.current(n,true));
   sale.setPaymentValidationStatus("APPROVED");assertTrue(policy.current(n,true));
   sale.setPaymentValidationStatus("REJECTED");assertFalse(policy.current(n,true));
 }
}
