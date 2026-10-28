package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WhatsappDepositWorkflowTest {
    JdbcTemplate jdbc; WhatsappDepositService workflow;
    final ClientBookingService bookings=mock(ClientBookingService.class);
    final AppointmentRepository appointments=mock(AppointmentRepository.class);
    final CustomerRepository customers=mock(CustomerRepository.class);
    final TenantPaymentMethodRepository methods=mock(TenantPaymentMethodRepository.class);
    final TenantTimeService time=mock(TenantTimeService.class);
    final TwilioDepositMediaDownloader downloader=mock(TwilioDepositMediaDownloader.class);
    Appointment appointment; TenantPaymentMethod yape; BookingBootstrapResponse catalog;CreateAppointmentRequest request;
    final String phone="+51999999999",sid="SM"+"b".repeat(32);
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("create table tenant(tenant_id bigint primary key)");jdbc.update("insert into tenant values (1)");
        jdbc.execute("create table customer(customer_id bigint primary key)");jdbc.update("insert into customer values (7)");
        jdbc.execute("create table appointment(appointment_id bigint primary key)");jdbc.update("insert into appointment values (99)");
        try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/manual/20261028_whatsapp_assistant_deposits.sql"));}
        workflow=new WhatsappDepositService(jdbc,bookings,appointments,customers,methods,time,downloader);
        ReflectionTestUtils.setField(workflow,"enabled",true);ReflectionTestUtils.setField(workflow,"entityManager",mock(EntityManager.class));
        Tenant tenant=new Tenant(1L);tenant.setNombre("Gods");tenant.setActive(true);
        Customer customer=Customer.builder().id(7L).tenant(tenant).telefono(phone).phoneVerified(true).whatsappTransactionalEnabled(true).build();
        appointment=Appointment.builder().id(99L).tenant(tenant).customer(customer).fecha(LocalDate.now(ZoneId.of("America/Lima")).plusDays(1)).horaInicio(LocalTime.of(17,0))
                .estado("PENDING_DEPOSIT_VALIDATION").depositRequired(true).depositStatus("AWAITING_EVIDENCE").totalAmount(new BigDecimal("50.00")).build();
        yape=TenantPaymentMethod.builder().id(4L).tenant(tenant).code("YAPE").accountLabel("Titular: Negocio Gods").accountValue("999999999").instructions("Verifica el titular antes de pagar").requiresOperationCode(true).active(true).build();
        when(methods.findByTenant_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(1L)).thenReturn(List.of(yape));
        when(methods.findByIdAndTenant_IdAndActiveTrue(4L,1L)).thenReturn(Optional.of(yape));
        when(customers.findByIdAndTenantIdWithTenant(7L,1L)).thenReturn(Optional.of(customer));
        when(appointments.findByIdAndTenant_IdAndCustomer_Id(99L,1L,7L)).thenReturn(Optional.of(appointment));
        when(appointments.findByIdAndTenant_Id(99L,1L)).thenReturn(Optional.of(appointment));
        when(appointments.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bookings.createWhatsappDepositHold(eq(1L),eq(7L),any())).thenReturn(CreateAppointmentResponse.builder().appointmentId(99L).estado("PENDING_DEPOSIT_VALIDATION").build());
        when(time.getZone(1L)).thenReturn(ZoneId.of("America/Lima"));when(time.now(1L)).thenReturn(LocalDateTime.now());
        catalog=BookingBootstrapResponse.builder().currency("PEN").bookingDepositEnabled(true).bookingDepositDefaultPercent(20).build();
        request=new CreateAppointmentRequest();request.setBranchId(2L);request.setServiceId(3L);request.setDate(appointment.getFecha().toString());request.setHoraInicio("17:00");request.setDepositAmount(new BigDecimal("10.00"));
        when(downloader.validSource(anyString(),anyString())).thenReturn(true);
    }
    void begin(){assertTrue(workflow.begin(1L,7L,phone,request,new BigDecimal("50.00"),catalog).contains("Horario apartado"));}
    String state(){return jdbc.queryForObject("select state from whatsapp_assistant_deposit where appointment_id=99",String.class);}
    Map<String,String> media(){return Map.of("MediaUrl0","https://api.twilio.com/media","MediaContentType0","image/jpeg");}
    void queueAndAttach(String reference) {
        workflow.inbound(phone,reference,sid,media(),Set.of(1L));
        jdbc.update("update whatsapp_assistant_deposit_media set state='PROCESSING' where message_sid=?",sid);
        workflow.attach(sid,"https://res.cloudinary.com/demo/comprobante.jpg");
    }
    @Test void instructionsUseBusinessConfigurationAndCorrectPercentageWithoutConfirmingPayment(){
        begin();assertEquals("AWAITING_EVIDENCE",state());assertEquals("AWAITING_EVIDENCE",appointment.getDepositStatus());
        assertTrue(jdbc.queryForObject("select instructions from whatsapp_assistant_deposit",String.class).contains("999999999"));
        assertEquals(new BigDecimal("10.00"),request.getDepositAmount());verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void wrongBranchCannotUseAnotherBranchesYape(){
        yape.setBranch(Branch.builder().id(999L).build());assertNull(workflow.offer(1L,2L,new BigDecimal("50.00"),catalog));
    }
    @Test void changedDepositMustBeReviewedBeforeAnyHoldOrPayment(){
        catalog.setBookingDepositDefaultPercent(40);
        assertTrue(workflow.begin(1L,7L,phone,request,new BigDecimal("50.00"),catalog).contains("cambio"));
        verifyNoInteractions(bookings);
    }
    @Test void repeatedStartDoesNotCreateAnotherHoldForSamePhone(){
        begin();assertTrue(workflow.begin(1L,7L,phone,request,new BigDecimal("50.00"),catalog).contains("Ya tienes"));
        verify(bookings,times(1)).createWhatsappDepositHold(any(),any(),any());
    }
    @Test void uploadIsQueuedAndDoesNotAutomaticallyApproveOrTrustTheCapture(){
        begin();String response=workflow.inbound(phone,"OPERACION 123456",sid,media(),Set.of(1L));
        assertTrue(response.contains("estamos adjuntando"));assertEquals("AWAITING_EVIDENCE",state());assertNull(appointment.getDepositEvidenceUrl());
        assertEquals(1,jdbc.queryForObject("select count(*) from whatsapp_assistant_deposit_media",Integer.class));
    }
    @Test void missingReferenceKeepsEvidenceAndAsksForReferenceThenBecomesPendingValidation(){
        begin();queueAndAttach("");assertEquals("AWAITING_REFERENCE",state());assertNotNull(appointment.getDepositEvidenceUrl());
        assertTrue(workflow.inbound(phone,"OPERACION 123456",sid,Map.of(),Set.of(1L)).contains("pendiente de revision"));
        assertEquals("PENDING_VALIDATION",state());assertEquals("PENDING_DEPOSIT_VALIDATION",appointment.getEstado());
        assertNotEquals("PAID",appointment.getDepositStatus());
    }
    @Test void ownerApprovalConfirmsAndQueuesOneNotificationOnlyAfterEvidence(){
        begin();assertThrows(IllegalStateException.class,()->workflow.beforeDecision(appointment));
        queueAndAttach("123456");
        var owner=new OwnerAppointmentDepositService(appointments,workflow);
        var approval=new ValidateAppointmentDepositRequest();approval.setApproved(true);
        assertEquals("CONFIRMED",owner.validateDeposit(1L,5L,99L,approval).getEstado());assertEquals("PAID",state());
        assertEquals(1,jdbc.queryForObject("select count(*) from whatsapp_assistant_deposit_notice where event_key like '%:PAID:%'",Integer.class));
        assertThrows(IllegalStateException.class,()->owner.validateDeposit(1L,5L,99L,approval));
    }
    @Test void rejectionAllowsNewEvidenceWithoutTreatingNewReferenceAsReplacementCapture(){
        begin();queueAndAttach("123456");
        var owner=new OwnerAppointmentDepositService(appointments,workflow);
        var rejection=new ValidateAppointmentDepositRequest();rejection.setApproved(false);rejection.setNote("No coincide la operacion");
        owner.validateDeposit(1L,5L,99L,rejection);assertEquals("REJECTED",state());
        workflow.inbound(phone,"OPERACION 654321",sid,Map.of(),Set.of(1L));assertEquals("REJECTED",state());
        assertTrue(jdbc.queryForObject("select body from whatsapp_assistant_deposit_notice where event_key like '%:REJECTED:%'",String.class).contains("No coincide"));
    }
    @Test void wrongTenantCannotAttachAndInvalidUrlOrTypeCannotQueue(){
        begin();assertTrue(workflow.inbound(phone,"",sid,media(),Set.of(2L)).contains("cuenta verificada"));
        assertTrue(workflow.inbound(phone,"",sid,Map.of("MediaUrl0","https://evil.example/receipt","MediaContentType0","application/pdf"),Set.of(1L)).contains("JPG"));
        assertEquals(0,jdbc.queryForObject("select count(*) from whatsapp_assistant_deposit_media",Integer.class));
    }
    @Test void expiredUnpaidHoldIsReleasedAndCannotLaterBeApproved(){
        begin();jdbc.update("update whatsapp_assistant_deposit set expires_at=?",Timestamp.from(Instant.now().minusSeconds(1)));
        workflow.expireDue();assertEquals("EXPIRED",state());assertEquals("CANCELADO",appointment.getEstado());
        assertThrows(IllegalStateException.class,()->workflow.beforeDecision(appointment));
    }
    @Test void backgroundUploadPersistsReceiptAndOutboxDoesNotResendAcceptedMessages() throws Exception {
        begin();workflow.inbound(phone,"123456",sid,media(),Set.of(1L));
        CloudinaryStorageService storage=mock(CloudinaryStorageService.class);TwilioDepositNoticeSender sender=mock(TwilioDepositNoticeSender.class);
        when(downloader.download(anyString(),anyString(),anyString())).thenReturn(new MockMultipartFile("file","receipt.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,1}));
        when(storage.uploadAppointmentDepositEvidence(eq(1L),eq(7L),any())).thenReturn(new CloudinaryStorageService.UploadResult("https://res.cloudinary.com/demo/receipt.jpg","receipt-public"));
        when(sender.send(anyString(),anyString(),any())).thenReturn("SM"+"c".repeat(32));
        var worker=new WhatsappDepositWorker(jdbc,workflow,downloader,storage,sender);worker.processMedia();
        assertEquals("PENDING_VALIDATION",state());assertEquals("DONE",jdbc.queryForObject("select state from whatsapp_assistant_deposit_media",String.class));
        worker.processNotices();worker.processNotices();verify(sender,times(1)).send(eq(phone),anyString(),any());
        assertEquals("SENT",jdbc.queryForObject("select state from whatsapp_assistant_deposit_notice",String.class));
    }
    @Test void disabledWorkflowDoesNotAccessNewTablesOrRepositories(){
        ReflectionTestUtils.setField(workflow,"enabled",false);
        assertNull(workflow.inbound(phone,"estado",sid,media(),Set.of(1L)));verifyNoInteractions(bookings,appointments,downloader,customers);
    }
    @Test void datesForNewBookingsAreNeverMistakenForPaymentReferences(){
        begin();assertNull(workflow.inbound(phone,"2026-11-02",sid,Map.of(),Set.of(1L)));
        assertNull(appointment.getDepositOperationCode());
        jdbc.update("update whatsapp_assistant_deposit set state='PAID'");
        assertNull(workflow.inbound(phone,"2026",sid,Map.of(),Set.of(1L)));
        assertEquals("AB-123",WhatsappDepositService.reference("OPERACION AB-123"));
    }
    @Test void aNewImageAfterRejectionRequiresANewReferenceAndKeepsPriorAttemptAudit(){
        begin();queueAndAttach("123456");
        var owner=new OwnerAppointmentDepositService(appointments,workflow);
        var rejection=new ValidateAppointmentDepositRequest();rejection.setApproved(false);owner.validateDeposit(1L,5L,99L,rejection);
        String another="SM"+"d".repeat(32);
        workflow.inbound(phone,"",another,media(),Set.of(1L));
        jdbc.update("update whatsapp_assistant_deposit_media set state='PROCESSING' where message_sid=?",another);
        workflow.attach(another,"https://res.cloudinary.com/demo/new-receipt.jpg");
        assertEquals("AWAITING_REFERENCE",state());
        assertEquals("123456",jdbc.queryForObject("select operation_reference from whatsapp_assistant_deposit_media where message_sid=?",String.class,sid));
        assertThrows(IllegalStateException.class,()->workflow.beforeDecision(appointment));
    }
}
