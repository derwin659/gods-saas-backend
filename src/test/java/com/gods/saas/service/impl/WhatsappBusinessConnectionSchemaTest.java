package com.gods.saas.service.impl;

import com.gods.saas.domain.model.WhatsappBusinessConnection;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WhatsappBusinessConnectionSchemaTest {
    @Test void realHibernateMappingReadsAndWritesTheMigratedReminderColumn() throws Exception {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        var source=new DriverManagerDataSource(url,"sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("create table tenant(tenant_id bigint primary key)");
        jdbc.execute("create table sale(sale_id bigint primary key)");
        jdbc.execute("create table notification(tenant_id bigint,customer_id bigint,type varchar(50),reference_type varchar(50),reference_id bigint)");
        jdbc.update("insert into tenant values(1)");
        try(var connection=source.getConnection()) {
            for(String script:new String[]{"20261103_whatsapp_embedded_signup.sql","20261103_whatsapp_sale_reminders.sql","20261103_whatsapp_post_sale_parity.sql"})
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/manual/"+script));
        }
        var config=new Configuration().addAnnotatedClass(WhatsappBusinessConnection.class);
        config.setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy());
        config.setProperty("hibernate.connection.url",url);
        config.setProperty("hibernate.connection.username","sa");
        config.setProperty("hibernate.connection.password","");
        config.setProperty("hibernate.hbm2ddl.auto","validate");
        try(var factory=config.buildSessionFactory()) {
            try(var session=factory.openSession()) {
                var tx=session.beginTransaction();
                var row=new WhatsappBusinessConnection();row.setTenantId(1L);row.setPhone("+51911111111");row.setReminder60ContentSid("HX"+"a".repeat(32));
                session.persist(row);tx.commit();
            }
            try(var session=factory.openSession()) {
                var rows=session.createQuery("from WhatsappBusinessConnection",WhatsappBusinessConnection.class).getResultList();
                assertEquals(1,rows.size());assertEquals("HX"+"a".repeat(32),rows.getFirst().getReminder60ContentSid());
            }
        }
        assertEquals("HX"+"a".repeat(32),jdbc.queryForObject("select reminder60_content_sid from whatsapp_business_connection where tenant_id=1",String.class));
    }
}
