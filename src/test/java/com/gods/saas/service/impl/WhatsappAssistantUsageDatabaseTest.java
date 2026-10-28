package com.gods.saas.service.impl;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.dto.response.*;
import com.gods.saas.service.impl.impl.SubscriptionService;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.math.BigDecimal;

/** Real SQL and transaction tests in H2 PostgreSQL mode; production PostgreSQL pilot still required. */
class WhatsappAssistantUsageDatabaseTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private WhatsappAssistantUsageService usage;
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean WhatsappAssistantUsageService usage(JdbcTemplate jdbc) { return new WhatsappAssistantUsageService(jdbc); }
    }
    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("create table tenant(tenant_id bigint primary key)");
        jdbc.update("insert into tenant values (1)");
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/manual/20261028_whatsapp_assistant.sql"));
        }
        usage = context.getBean(WhatsappAssistantUsageService.class);
    }
    @AfterEach void cleanup() { context.close(); }
    @Test void providerFailureRollbackDoesNotRestoreAttemptAllowance() {
        var outer = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class, () -> outer.execute(status -> {
            jdbc.update("insert into whatsapp_assistant_message(message_sid) values (?)", "SM-one");
            assertTrue(usage.reserve(1L,"2026-10",10));
            throw new IllegalStateException("provider failure");
        }));
        assertEquals(1,usage.used(1L,"2026-10"));
        assertEquals(0,jdbc.queryForObject("select count(*) from whatsapp_assistant_message",Integer.class));
    }
    @Test void concurrentAttemptsCannotExceedMonthlyQuota() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int i=0;i<25;i++) attempts.add(() -> usage.reserve(1L,"2026-10",10));
            int accepted=0;
            for (Future<Boolean> result : pool.invokeAll(attempts)) if (result.get()) accepted++;
            assertEquals(10,accepted);
            assertEquals(10,usage.used(1L,"2026-10"));
            assertTrue(usage.reserve(1L,"2026-11",10));
            assertEquals(1,usage.used(1L,"2026-11"));
        } finally { pool.shutdownNow(); }
    }
    @Test void duplicateMessageSidAndSessionInitializationAreIdempotent() {
        assertEquals(1,jdbc.update("insert into whatsapp_assistant_message(message_sid) values (?) on conflict do nothing","SM-two"));
        assertEquals(0,jdbc.update("insert into whatsapp_assistant_message(message_sid) values (?) on conflict do nothing","SM-two"));
        jdbc.update("insert into whatsapp_assistant_session(phone) values (?) on conflict do nothing","+51999999999");
        jdbc.update("insert into whatsapp_assistant_session(phone) values (?) on conflict do nothing","+51999999999");
        assertEquals(1,jdbc.queryForObject("select count(*) from whatsapp_assistant_session",Integer.class));
    }
    @Test void fullConversationPersistsAcrossWebhookRequestsAndDuplicateConfirmationDoesNotRepeatBooking() throws Exception {
        var customers=mock(CustomerRepository.class);
        var tenants=mock(TenantRepository.class);
        var loyalty=mock(LoyaltyAccountRepository.class);
        var promotions=mock(PromotionRepository.class);
        var subscriptions=mock(SubscriptionService.class);
        var bookings=mock(ClientBookingService.class);
        var time=mock(TenantTimeService.class);
        var policy=mock(WhatsappAssistantPolicy.class);
        var interpreter=mock(WhatsappAssistantInterpreter.class);
        var service=new WhatsappAssistantService(jdbc,new ObjectMapper(),customers,tenants,loyalty,promotions,subscriptions,bookings,time,policy,interpreter,usage,mock(WhatsappDepositService.class),mock(WhatsappCustomerOnboarding.class));
        ReflectionTestUtils.setField(service,"enabled",true);
        ReflectionTestUtils.setField(service,"tenantIds","1");
        ReflectionTestUtils.setField(service,"fromNumber","+15005550006");
        when(interpreter.configured()).thenReturn(true);
        Tenant tenant=new Tenant(1L);tenant.setNombre("Barberia Test");tenant.setActive(true);
        Customer customer=Customer.builder().id(7L).tenant(tenant).phoneVerified(true).whatsappTransactionalEnabled(true).build();
        when(customers.findActiveByPhoneAcrossTenants("+51999999999")).thenReturn(List.of(customer));
        when(customers.findByTenantIdAndTelefonoWithTenant(1L,"+51999999999")).thenReturn(Optional.of(customer));
        Subscription subscription=new Subscription();subscription.setPlan("PRO_LEGACY");
        when(subscriptions.getCurrentSubscriptionOrThrow(1L)).thenReturn(subscription);
        when(policy.monthlyTurns("PRO_LEGACY")).thenReturn(600);
        when(time.today(1L)).thenReturn(LocalDate.of(2026,10,28));
        when(loyalty.findByTenant_IdAndCustomer_Id(1L,7L)).thenReturn(Optional.of(LoyaltyAccount.builder().puntosDisponibles(40).build()));
        when(promotions.findActiveClientPromotions(1L,40)).thenReturn(List.of());
        when(bookings.getBootstrap(1L)).thenReturn(BookingBootstrapResponse.builder().tenantName("Barberia Test").currency("PEN")
                .branches(List.of(BranchMiniResponse.builder().id(2L).nombre("Centro").build()))
                .services(List.of(ServiceMiniResponse.builder().id(3L).nombre("Corte").precio(25.0).build()))
                .barbers(List.of()).bookingDepositEnabled(false).build());
        when(bookings.getAvailability(1L,2L,3L,"2026-10-29",null)).thenReturn(BookingAvailabilityResponse.builder().slots(List.of("17:00")).build());
        when(bookings.quoteAssistantAppointment(eq(1L),any())).thenReturn(new BigDecimal("25.00"));
        when(bookings.createAppointment(eq(1L),eq(7L),any())).thenReturn(CreateAppointmentResponse.builder().appointmentId(99L).estado("CONFIRMED").build());
        String[] inputs={"hola","1","1","mañana","1","si"};
        String[] outputs={"Barberia Test","¿Para que servicio","¿Para que dia","17:00","¿Confirmo tu reserva?","#99"};
        for (int i=0;i<inputs.length;i++) {
            // Simulate time between customer replies without a wall-clock sleep.
            jdbc.update("update whatsapp_assistant_session set last_turn_at=null");
            String response=service.reply("whatsapp:+51999999999","whatsapp:+15005550006",inputs[i],"SM"+String.format("%032x",i+1));
            assertNotNull(response);assertTrue(response.contains(outputs[i]),response);
            if (i<5) verify(bookings,never()).createAppointment(any(),any(),any());
        }
        assertEquals(6,usage.used(1L,"2026-10"));
        assertNull(jdbc.queryForObject("select draft from whatsapp_assistant_session where phone=?",String.class,"+51999999999"));
        assertNull(service.reply("whatsapp:+51999999999","whatsapp:+15005550006","si","SM"+String.format("%032x",6)));
        verify(bookings,times(1)).createAppointment(eq(1L),eq(7L),any());
        verify(interpreter,never()).interpret(anyString(),anyString());
    }
}
