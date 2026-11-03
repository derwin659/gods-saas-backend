package com.gods.saas.service.impl;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class WhatsappOnboardingPersistenceTest {
    @Test void migrationIsRepeatableAndAnotherTenantCannotClaimTheSameNumberOrWaba() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);jdbc.execute("create table tenant(tenant_id bigint primary key)");jdbc.update("insert into tenant values (1),(2)");
        try(var connection=source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/manual/20261103_whatsapp_embedded_signup.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/manual/20261103_whatsapp_embedded_signup.sql"));
        }
        jdbc.update("insert into whatsapp_business_connection(tenant_id,phone,waba_id,state) values (1,'+51911111111','123456','RESERVED')");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into whatsapp_business_connection(tenant_id,phone,state) values (2,'+51911111111','RESERVED')"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into whatsapp_business_connection(tenant_id,phone,waba_id,state) values (2,'+51922222222','123456','RESERVED')"));
        assertEquals(1,jdbc.queryForObject("select count(*) from whatsapp_business_connection",Integer.class));
    }
}
