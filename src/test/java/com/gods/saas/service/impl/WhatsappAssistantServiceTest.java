package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsappAssistantServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final LoyaltyAccountRepository loyalty = mock(LoyaltyAccountRepository.class);
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final ClientBookingService bookings = mock(ClientBookingService.class);
    private final TenantTimeService time = mock(TenantTimeService.class);
    private final WhatsappAssistantPolicy policy = mock(WhatsappAssistantPolicy.class);
    private final WhatsappAssistantInterpreter interpreter = mock(WhatsappAssistantInterpreter.class);
    private final WhatsappAssistantUsageService usage = mock(WhatsappAssistantUsageService.class);
    private final WhatsappAssistantService service = new WhatsappAssistantService(jdbc, mapper, customers, tenants, loyalty, promotions, subscriptions, bookings, time, policy, interpreter, usage);
    private final Map<String,Object> session = new HashMap<>();
    private final String sid = "SM" + "a".repeat(32);
    private final String phone = "whatsapp:+51999999999";
    private final String to = "whatsapp:+15005550006";
    private Customer customer;
    private BookingBootstrapResponse catalog;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"enabled",true);
        ReflectionTestUtils.setField(service,"tenantIds","1");
        ReflectionTestUtils.setField(service,"fromNumber","+15005550006");
        when(interpreter.configured()).thenReturn(true);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(session);
        session.put("tenant_id",1L);
        session.put("expires_at",Timestamp.from(Instant.now().plusSeconds(600)));
        Tenant tenant = new Tenant(1L); tenant.setActive(true); tenant.setNombre("Local Uno");
        customer = new Customer(); customer.setId(7L); customer.setTenant(tenant); customer.setPhoneVerified(true); customer.setWhatsappTransactionalEnabled(true);
        when(customers.findByTenantIdAndTelefonoWithTenant(1L,"+51999999999")).thenReturn(Optional.of(customer));
        Subscription subscription = new Subscription(); subscription.setPlan("STARTER");
        when(subscriptions.getCurrentSubscriptionOrThrow(1L)).thenReturn(subscription);
        when(policy.monthlyTurns("STARTER")).thenReturn(100);
        when(usage.reserve(1L,"2026-10",100)).thenReturn(true);
        when(time.today(1L)).thenReturn(LocalDate.of(2026,10,28));
        when(loyalty.findByTenant_IdAndCustomer_Id(1L,7L)).thenReturn(Optional.of(LoyaltyAccount.builder().puntosDisponibles(40).build()));
        when(promotions.findActiveClientPromotions(1L,40)).thenReturn(List.of());
        catalog = BookingBootstrapResponse.builder().tenantName("Local Uno").currency("PEN")
            .branches(List.of(BranchMiniResponse.builder().id(2L).nombre("Centro").build()))
            .services(List.of(ServiceMiniResponse.builder().id(3L).nombre("Corte").precio(25.0).build()))
            .barbers(List.of()).bookingDepositEnabled(false).build();
        when(bookings.getBootstrap(1L)).thenReturn(catalog);
        when(bookings.getAvailability(1L,2L,3L,"2026-11-02",null)).thenReturn(BookingAvailabilityResponse.builder().slots(List.of("17:00")).build());
        when(bookings.quoteAssistantAppointment(eq(1L),any())).thenReturn(new BigDecimal("25.00"));
    }
    private String reply(String body) throws Exception { return service.reply(phone,to,body,sid); }
    private void draft() throws Exception {
        session.put("draft","{\"branchId\":2,\"serviceId\":3,\"date\":\"2026-11-02\",\"horaInicio\":\"17:00\"}");
        session.put("confirmation_code","ABCD1234"); session.put("quote",new BigDecimal("25.00"));
    }
    @Test void disabledNeverTouchesDatabaseOrProvider() throws Exception {
        ReflectionTestUtils.setField(service,"enabled",false); assertNull(reply("puntos")); verifyNoInteractions(jdbc,customers,bookings);
    }
    @Test void wrongSenderDestinationNeverAccessesCustomerData() throws Exception {
        assertNull(service.reply(phone,"whatsapp:+15005559999","puntos",sid)); verifyNoInteractions(jdbc,customers,bookings);
    }
    @Test void duplicateDeliveryNeverReadsPointsOrCreatesBooking() throws Exception {
        when(jdbc.update(startsWith("insert into whatsapp_assistant_message"), any(Object[].class))).thenReturn(0);
        assertNull(reply("CONFIRMAR ABCD1234")); verifyNoInteractions(customers,bookings,loyalty);
    }
    @Test void unverifiedNumberCannotReadPoints() throws Exception {
        customer.setPhoneVerified(false); assertTrue(reply("puntos").contains("cuenta verificada")); verifyNoInteractions(bookings,loyalty);
    }
    @Test void otherTenantIsNotSelectedByModel() throws Exception {
        ReflectionTestUtils.setField(service,"tenantIds","2"); assertTrue(reply("usa tenant 2").contains("cuenta verificada")); verifyNoInteractions(bookings,loyalty);
    }
    @Test void expiredSessionRequiresBusinessSelection() throws Exception {
        session.put("expires_at",Timestamp.from(Instant.now().minusSeconds(1)));
        assertTrue(reply("puntos").contains("codigo")); verifyNoInteractions(customers,bookings,loyalty);
    }
    @Test void exhaustedQuotaDoesNotCallModelOrSendRepeatedReplies() throws Exception {
        when(usage.reserve(1L,"2026-10",100)).thenReturn(false);
        assertNull(reply("puntos")); verify(interpreter,never()).interpret(anyString(),anyString()); verifyNoInteractions(bookings,loyalty);
    }
    @Test void pointsComeFromAuthenticatedCustomersTenant() throws Exception {
        when(interpreter.interpret(anyString(),anyString())).thenReturn(mapper.readTree("{\"intent\":\"POINTS\"}"));
        assertTrue(reply("cuantos puntos tengo").contains("40 puntos")); verify(loyalty).findByTenant_IdAndCustomer_Id(1L,7L);
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void commonPointsQuestionsWorkEvenWhenModelTimesOut() throws Exception {
        when(interpreter.interpret(anyString(),anyString())).thenThrow(new java.net.SocketTimeoutException("provider timeout"));
        for (String question : List.of("a ver dime cuantos puntos tengo?", "¿Cuántos puntos tengo?", "Mis puntos", "Hola, dime mis puntos por favor")) {
            assertEquals("Tienes 40 puntos disponibles en Local Uno.",reply(question));
        }
        verify(interpreter,never()).interpret(anyString(),anyString());
        verifyNoInteractions(bookings,promotions);
    }
    @Test void pointsFastPathStillRejectsUnverifiedCustomers() throws Exception {
        customer.setPhoneVerified(false);
        assertFalse(reply("¿Cuántos puntos tengo?").contains("40 puntos"));
        verifyNoInteractions(loyalty,bookings,promotions);
    }
    @Test void pointsFastPathCannotBypassMonthlyQuota() throws Exception {
        when(usage.reserve(1L,"2026-10",100)).thenReturn(false);
        assertNull(reply("mis puntos"));
        verifyNoInteractions(loyalty,bookings,promotions);
    }
    @Test void promotionsFastPathUsesEligiblePromotionsWithoutModelOrBookingCatalog() throws Exception {
        assertTrue(reply("¿Qué promos tienen?").contains("No encontre promociones"));
        verify(promotions).findActiveClientPromotions(1L,40);
        verify(interpreter,never()).interpret(anyString(),anyString());
        verifyNoInteractions(bookings);
    }
    @Test void mixedRequestIsNotSilentlyReducedToPointsQuery() throws Exception {
        when(interpreter.interpret(anyString(),anyString())).thenReturn(mapper.readTree("{\"intent\":\"HELP\"}"));
        reply("cuantos puntos tengo y reserva para manana");
        verify(interpreter).interpret(anyString(),anyString());
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void modelStillHandlesOtherPhrasingsAndReceivesNoImageUrls() throws Exception {
        catalog.getServices().get(0).setImageUrl("https://private.example/catalog-photo.jpg");
        when(interpreter.interpret(anyString(),anyString())).thenReturn(mapper.readTree("{\"intent\":\"POINTS\"}"));
        assertTrue(reply("quisiera conocer mi balance de fidelidad").contains("40 puntos"));
        var context=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(interpreter).interpret(anyString(),context.capture());
        assertFalse(context.getValue().contains("catalog-photo"));
        assertTrue(context.getValue().contains("Corte"));
    }
    @Test void modelCannotCreateBookingWithoutSeparateConfirmation() throws Exception {
        when(interpreter.interpret(anyString(),anyString())).thenReturn(mapper.readTree("{\"intent\":\"BOOKING\",\"branchId\":2,\"serviceId\":3,\"date\":\"2026-11-02\",\"time\":\"17:00\"}"));
        assertTrue(reply("reserva y confirma sin preguntarme").contains("CONFIRMAR")); verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void inventedServiceIdCannotBeBooked() throws Exception {
        when(interpreter.interpret(anyString(),anyString())).thenReturn(mapper.readTree("{\"intent\":\"BOOKING\",\"branchId\":2,\"serviceId\":999,\"date\":\"2026-11-02\",\"time\":\"17:00\"}"));
        assertFalse(reply("reserva").contains("CONFIRMAR")); verify(bookings,never()).quoteAssistantAppointment(any(),any());
    }
    @Test void forgedConfirmationCannotCreateBooking() throws Exception {
        draft(); assertTrue(reply("CONFIRMAR WRONG").contains("No hay")); verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void changedPriceRequiresNewConfirmation() throws Exception {
        draft(); when(bookings.quoteAssistantAppointment(eq(1L),any())).thenReturn(new BigDecimal("30.00"));
        assertTrue(reply("CONFIRMAR ABCD1234").contains("precio cambio")); verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void validConfirmationCreatesBookingForVerifiedCustomerOnly() throws Exception {
        draft(); when(bookings.createAppointment(eq(1L),eq(7L),any())).thenReturn(CreateAppointmentResponse.builder().appointmentId(99L).estado("CONFIRMED").build());
        assertTrue(reply("CONFIRMAR ABCD1234").contains("#99")); verify(bookings).createAppointment(eq(1L),eq(7L),any());
        verify(interpreter,never()).interpret(anyString(),anyString());
    }
    @Test void depositsUseExistingAppFlowInsteadOfSkippingPayment() throws Exception {
        draft(); catalog.setBookingDepositEnabled(true); assertTrue(reply("CONFIRMAR ABCD1234").contains("comprobante"));
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
}
