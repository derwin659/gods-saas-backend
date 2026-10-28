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
    private final WhatsappDepositService deposits = mock(WhatsappDepositService.class);
    private final WhatsappCustomerOnboarding onboarding=mock(WhatsappCustomerOnboarding.class);
    private final WhatsappAssistantService service = new WhatsappAssistantService(jdbc, mapper, customers, tenants, loyalty, promotions, subscriptions, bookings, time, policy, interpreter, usage, deposits, onboarding, mock(WhatsappCustomerActions.class));
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
        assertTrue(reply("puntos").contains("codigo")); verify(customers).findActiveByPhoneAcrossTenants("+51999999999"); verifyNoInteractions(bookings,loyalty);
    }
    @Test void exhaustedQuotaDoesNotCallModelOrSendRepeatedReplies() throws Exception {
        when(usage.reserve(1L,"2026-10",100)).thenReturn(false);
        assertTrue(reply("puntos").contains("limite mensual")); verify(interpreter,never()).interpret(anyString(),anyString()); verifyNoInteractions(bookings,loyalty);
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
        trackSession();
        assertTrue(reply("mis puntos").contains("limite mensual"));
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
    /** Persist only the session mutations; each test turn represents a new customer message. */
    private void trackSession() {
        doAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            Object[] args=(Object[])invocation.getRawArguments()[1];
            if (sql.startsWith("update whatsapp_assistant_session set draft=?")) {
                session.put("draft",args[0]); session.remove("confirmation_code"); session.remove("quote");
            } else if (sql.startsWith("update whatsapp_assistant_session set confirmation_code=?")) {
                session.put("confirmation_code",args[0]); session.put("quote",args[1]);
            } else if (sql.startsWith("update whatsapp_assistant_session set draft=null")) {
                session.remove("draft"); session.remove("confirmation_code"); session.remove("quote");
            } else if (sql.startsWith("update whatsapp_assistant_session set tenant_id=null,draft=null")) {
                session.remove("tenant_id");session.remove("draft");session.remove("quote");session.remove("confirmation_code");
            } else if (sql.startsWith("update whatsapp_assistant_session set tenant_id=?")) {
                session.put("tenant_id",args[0]);
            }
            return 1;
        }).when(jdbc).update(anyString(),any(Object[].class));
        when(bookings.getAvailability(1L,2L,3L,"2026-10-29",null)).thenReturn(BookingAvailabilityResponse.builder().slots(List.of("17:00","18:00")).build());
    }
    @Test void greetingAutomaticallySelectsOnlyVerifiedAllowedBusiness() throws Exception {
        session.remove("tenant_id");
        when(customers.findActiveByPhoneAcrossTenants("+51999999999")).thenReturn(List.of(customer));
        trackSession();
        assertTrue(reply("Hola").contains("Local Uno"));
        assertEquals(1L,session.get("tenant_id"));
        assertTrue(mapper.readTree(session.get("draft").toString()).path("_assistant").path("stage").asText().equals("MENU"));
        verify(interpreter,never()).interpret(anyString(),anyString());
    }
    @Test void multipleBusinessesRequireExplicitChoiceAndExcludeUnverifiedAccounts() throws Exception {
        session.remove("tenant_id"); trackSession();
        var other=Customer.builder().id(9L).tenant(new Tenant(2L)).phoneVerified(true).whatsappTransactionalEnabled(true).build();
        other.getTenant().setNombre("Local Dos");
        var hidden=Customer.builder().id(10L).tenant(new Tenant(3L)).phoneVerified(false).whatsappTransactionalEnabled(true).build();
        hidden.getTenant().setNombre("Privado");
        ReflectionTestUtils.setField(service,"tenantIds","1,2,3");
        Subscription sub=new Subscription();sub.setPlan("PRO");
        when(subscriptions.getCurrentSubscriptionOrThrow(2L)).thenReturn(sub);
        when(policy.monthlyTurns("PRO")).thenReturn(600);
        when(time.today(2L)).thenReturn(LocalDate.of(2026,10,28));
        when(usage.reserve(2L,"2026-10",600)).thenReturn(true);
        when(customers.findActiveByPhoneAcrossTenants("+51999999999")).thenReturn(List.of(customer,other,hidden));
        String prompt=reply("hola");
        assertTrue(prompt.contains("1. Local Uno"));assertTrue(prompt.contains("2. Local Dos"));assertFalse(prompt.contains("Privado"));
        assertNull(session.get("tenant_id"));
        assertTrue(reply("2").contains("Estas en Local Dos"));assertEquals(2L,session.get("tenant_id"));
        verifyNoInteractions(loyalty,bookings);
    }
    @Test void completeNumberedConversationKeepsMemoryAndCreatesOnlyAfterYes() throws Exception {
        trackSession();
        assertTrue(reply("hola").contains("1. Reservar"));
        assertTrue(reply("1").contains("¿Para que servicio"));
        assertTrue(reply("1").contains("¿Para que dia"));
        assertTrue(reply("mañana").contains("17:00"));
        String summary=reply("el segundo");
        assertTrue(summary.contains("18:00"));assertTrue(summary.contains("Total: PEN 25.00"));
        verify(bookings,never()).createAppointment(any(),any(),any());
        // A points detour keeps the draft and confirmation intact.
        assertTrue(reply("¿Cuántos puntos tengo?").contains("40 puntos"));
        when(bookings.createAppointment(eq(1L),eq(7L),any())).thenReturn(CreateAppointmentResponse.builder().appointmentId(101L).estado("CONFIRMED").build());
        assertTrue(reply("sí").contains("#101"));
        var request=org.mockito.ArgumentCaptor.forClass(com.gods.saas.domain.dto.request.CreateAppointmentRequest.class);
        verify(bookings).createAppointment(eq(1L),eq(7L),request.capture());
        assertEquals("2026-10-29",request.getValue().getDate());assertEquals("18:00",request.getValue().getHoraInicio());
        verify(interpreter,never()).interpret(anyString(),anyString());
    }
    @Test void freeTextFollowupsMergeOnlyNewFieldsAndUsePreviousPromptContext() throws Exception {
        trackSession();
        when(interpreter.interpret(eq("quiero atenderme"),anyString())).thenReturn(mapper.readTree("{\"intent\":\"BOOKING\",\"serviceId\":3}"));
        assertTrue(reply("quiero atenderme").contains("¿Para que dia"));
        when(interpreter.interpret(eq("el dia siguiente temprano"),anyString())).thenReturn(mapper.readTree("{\"intent\":\"CONTINUE\",\"date\":\"2026-10-29\"}"));
        assertTrue(reply("el dia siguiente temprano").contains("17:00"));
        var state=mapper.readTree(session.get("draft").toString());
        assertEquals(3L,state.path("serviceId").asLong());assertEquals(2L,state.path("branchId").asLong());
        assertEquals("2026-10-29",state.path("date").asText());
        var context=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(interpreter).interpret(eq("el dia siguiente temprano"),context.capture());
        assertTrue(context.getValue().contains("lastPrompt"));assertTrue(context.getValue().contains("DATE"));
    }
    @Test void modelTimeoutReturnsSavedOptionsAndNumbersCanContinue() throws Exception {
        trackSession(); reply("hola"); reply("1");
        when(interpreter.interpret(anyString(),anyString())).thenThrow(new org.springframework.web.client.RestClientException("private",new java.net.SocketTimeoutException()));
        String fallback=reply("me gustaria arreglarme el cabello");
        assertTrue(fallback.contains("esta tardando"));assertTrue(fallback.contains("Corte"));assertFalse(fallback.contains("private"));
        assertTrue(reply("1").contains("¿Para que dia"));
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void noDeclinesPendingBookingWithoutCreatingOrCancellingAppointments() throws Exception {
        trackSession();reply("hola");reply("1");reply("1");reply("mañana");reply("1");
        assertTrue(reply("no").contains("No se creo ninguna cita"));
        verify(bookings,never()).createAppointment(any(),any(),any());
        verify(bookings,never()).cancelAppointment(any(),any(),any());
        assertFalse(session.containsKey("confirmation_code"));
    }
    @Test void availabilityConsultationNeverCreatesBookingAndHonorsTimeFilter() throws Exception {
        trackSession();
        when(interpreter.interpret(anyString(),anyString())).thenReturn(mapper.readTree("{\"intent\":\"AVAILABILITY\",\"serviceId\":3,\"date\":\"2026-10-29\",\"afterTime\":\"18:00\"}"));
        String response=reply("que horas hay mañana desde las seis para corte");
        assertTrue(response.contains("18:00"));assertFalse(response.contains("17:00"));
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void availableBarbersComeFromLiveSchedulesAndAssignments() throws Exception {
        trackSession();
        var luis=BarberMiniResponse.builder().id(8L).nombre("Luis").branchIds(List.of(2L)).serviceIdsByBranch(Map.of(2L,List.of(3L))).build();
        var busy=BarberMiniResponse.builder().id(9L).nombre("Ocupado").branchIds(List.of(2L)).serviceIdsByBranch(Map.of(2L,List.of(3L))).build();
        var incompatible=BarberMiniResponse.builder().id(10L).nombre("Otro servicio").branchIds(List.of(2L)).serviceIdsByBranch(Map.of(2L,List.of(99L))).build();
        catalog.setBarbers(List.of(luis,busy,incompatible));
        when(bookings.getAvailability(1L,2L,3L,"2026-10-29",8L)).thenReturn(BookingAvailabilityResponse.builder().slots(List.of("17:00")).build());
        when(bookings.getAvailability(1L,2L,3L,"2026-10-29",9L)).thenReturn(BookingAvailabilityResponse.builder().slots(List.of()).build());
        when(interpreter.interpret(anyString(),anyString())).thenReturn(mapper.readTree("{\"intent\":\"BARBERS\",\"serviceId\":3,\"date\":\"2026-10-29\"}"));
        String response=reply("quien esta libre mañana para corte");
        assertTrue(response.contains("Luis"));assertFalse(response.contains("Ocupado"));assertFalse(response.contains("Otro servicio"));
        verify(bookings,never()).getAvailability(1L,2L,3L,"2026-10-29",10L);
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void pastOrInvalidDateAsksAgainInsteadOfCallingAvailability() throws Exception {
        trackSession();
        when(interpreter.interpret(anyString(),anyString())).thenReturn(mapper.readTree("{\"intent\":\"BOOKING\",\"serviceId\":3,\"date\":\"2026-02-31\"}"));
        assertTrue(reply("reserva en una fecha invalida").contains("No entendi la fecha"));
        verify(bookings,never()).getAvailability(any(),any(),any(),anyString(),any());
    }
    @Test void menuPreservesChosenServiceButInvalidatesOldConfirmation() throws Exception {
        trackSession();reply("hola");reply("1");reply("1");reply("mañana");reply("1");
        assertTrue(session.containsKey("confirmation_code"));
        assertTrue(reply("menu").contains("Tu seleccion anterior"));
        assertEquals(3L,mapper.readTree(session.get("draft").toString()).path("serviceId").asLong());
        assertFalse(session.containsKey("confirmation_code"));
        assertTrue(reply("2").contains("40 puntos"));
        assertTrue(reply("1").contains("¿Confirmo tu reserva?"));
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void unknownNumericOptionRepeatsQuestionWithoutAskingModelToGuess() throws Exception {
        trackSession();reply("hola");reply("1");
        assertTrue(reply("19").contains("¿Para que servicio"));
        verify(interpreter,never()).interpret(anyString(),anyString());
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
    @Test void helloDoesNotDependOnModelCredentials() throws Exception {
        trackSession();when(interpreter.configured()).thenReturn(false);
        assertTrue(reply("Hola").contains("1. Reservar una cita"));
        verify(interpreter,never()).interpret(anyString(),anyString());
        verifyNoInteractions(bookings,loyalty,promotions);
    }
    @Test void freshOnboardingAlsoWorksWithoutModelCredentials() throws Exception {
        trackSession();session.remove("tenant_id");when(interpreter.configured()).thenReturn(false);
        when(customers.findActiveByPhoneAcrossTenants("+51999999999")).thenReturn(List.of(customer));
        assertTrue(reply("Hola").contains("1. Reservar una cita"));
        verify(interpreter,never()).interpret(anyString(),anyString());
    }
    @Test void depositInstructionsAreOnlyIssuedAfterExplicitConfirmationAndUseHoldNotConfirmedBooking() throws Exception {
        trackSession();catalog.setBookingDepositEnabled(true);
        when(deposits.supported()).thenReturn(true);
        when(deposits.offer(eq(1L),eq(2L),any(),eq(catalog))).thenReturn(new WhatsappDepositService.Offer(4L,new BigDecimal("10.00"),"Yape del negocio"));
        when(deposits.begin(eq(1L),eq(7L),eq("+51999999999"),any(),any(),eq(catalog))).thenReturn("Horario apartado. Envia captura del Yape.");
        reply("hola");reply("1");reply("1");reply("mañana");
        assertTrue(reply("1").contains("Adelanto requerido: PEN 10.00"));
        verify(deposits,never()).begin(any(),any(),anyString(),any(),any(),any());
        assertTrue(reply("si").contains("Envia captura"));
        verify(deposits).begin(eq(1L),eq(7L),eq("+51999999999"),any(),eq(new BigDecimal("25.00")),eq(catalog));
        verify(bookings,never()).createAppointment(any(),any(),any());
    }

    @Test void explicitBusinessSelectionRoutesToOnboardingEvenWithExistingSession() throws Exception {
        when(onboarding.reply(anyString(),anyString(),any(),any())).thenReturn("Seleccion de barberia");
        assertEquals("Seleccion de barberia",reply("Quiero reservar en INDYSTRY"));
        assertEquals("Seleccion de barberia",reply("GODS INDYSTRY"));
        verify(onboarding).reply(eq("+51999999999"),eq("INDYSTRY"),any(),any());
        verifyNoInteractions(loyalty,bookings);
    }
    @Test void ownerBookingLinkUsesAuthenticatedBusinessAndConfiguredCentralNumber() {
        customer.getTenant().setCodigo("INDYSTRY");
        when(tenants.findById(1L)).thenReturn(Optional.of(customer.getTenant()));
        var status=service.status(1L);
        assertEquals("INDYSTRY",status.get("businessCode"));
        assertEquals("https://wa.me/15005550006?text=GODS+INDYSTRY",status.get("bookingWhatsappUrl"));
        ReflectionTestUtils.setField(service,"tenantIds","2");
        assertEquals("",service.status(1L).get("bookingWhatsappUrl"));
    }

    @Test void numberedPromotionsReplyDoesNotNeedCatalogOrOpenAIAndDoesNotRepeatTitles() throws Exception {
        trackSession();reply("hola");
        when(promotions.findActiveClientPromotions(1L,40)).thenReturn(List.of(Promotion.builder().id(11L).titulo("Promo especial").descripcion("Para corte y barba").priceText("PEN 20").build()));
        String answer=reply("3");
        assertTrue(answer.contains("1. Promo especial"));assertEquals(answer.indexOf("Promo especial"),answer.lastIndexOf("Promo especial"));
        verify(bookings,never()).getBootstrap(anyLong());verify(interpreter,never()).interpret(anyString(),anyString());
    }
    @Test void emptyPromotionsExplainAvailabilityAndKeepMenuUsable() throws Exception {
        trackSession();reply("hola");assertTrue(reply("3").contains("No encontre promociones"));
        assertTrue(reply("menu").contains("7. Ver y canjear premios"));
        assertTrue(reply("5").contains("consultar barberos disponibles"));
    }
    @Test void barbersShortcutExplainsNeededInputsAndContinuesThroughRealAvailability() throws Exception {
        trackSession();reply("hola");
        catalog.setBarbers(List.of(BarberMiniResponse.builder().id(12L).nombre("Carlos").branchId(2L).build()));
        when(bookings.getAvailability(1L,2L,3L,"2026-10-29",12L)).thenReturn(BookingAvailabilityResponse.builder().slots(List.of("17:00")).build());
        assertTrue(reply("5").contains("necesito el servicio y el dia"));
        assertTrue(reply("1").contains("¿Para que dia"));
        String answer=reply("mañana");assertTrue(answer.contains("Carlos: 17:00"));
        verify(bookings,never()).createAppointment(any(),any(),any());verify(interpreter,never()).interpret(anyString(),anyString());
    }

    @Test void throttledUserSeesRetryInstructionWithoutExecutingActions() throws Exception {
        session.put("last_turn_at",Timestamp.from(Instant.now()));
        assertTrue(reply("3").contains("vuelve a enviar"));verifyNoInteractions(customers,bookings,loyalty);
    }

    @Test void variablePriceServiceUsesStoredBaseAndCanBeBookedWithoutRepeatingServiceSelection() throws Exception {
        trackSession();catalog.getServices().get(0).setPrecioVariable(true);
        reply("hola");reply("1");assertTrue(reply("1").contains("¿Para que dia"));reply("mañana");
        String summary=reply("1");assertTrue(summary.contains("Precio desde (base de la reserva): PEN 25.00"));
        when(bookings.createAppointment(eq(1L),eq(7L),any())).thenReturn(CreateAppointmentResponse.builder().appointmentId(111L).estado("CREATED").build());
        assertTrue(reply("SI").contains("#111"));verify(bookings).createAppointment(eq(1L),eq(7L),any());
    }
    @Test void variablePriceWithDepositUsesBaseQuoteForHoldAndNeverConfirmsPayment() throws Exception {
        trackSession();catalog.getServices().get(0).setPrecioVariable(true);catalog.setBookingDepositEnabled(true);
        when(deposits.supported()).thenReturn(true);
        when(deposits.offer(eq(1L),eq(2L),eq(new BigDecimal("25.00")),eq(catalog))).thenReturn(new WhatsappDepositService.Offer(4L,new BigDecimal("5.00"),"Yape"));
        when(deposits.begin(eq(1L),eq(7L),anyString(),any(),eq(new BigDecimal("25.00")),eq(catalog))).thenReturn("Horario apartado");
        reply("hola");reply("1");reply("1");reply("mañana");
        assertTrue(reply("1").contains("Adelanto requerido: PEN 5.00"));assertEquals("Horario apartado",reply("SI"));
        verify(bookings,never()).createAppointment(any(),any(),any());
    }
}
