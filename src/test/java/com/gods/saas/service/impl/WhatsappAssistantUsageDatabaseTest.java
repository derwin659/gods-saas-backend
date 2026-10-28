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
}
