package com.gods.saas.service.impl;
import com.gods.saas.domain.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GoogleReviewHistoryQueryTest {
 @Test void migratedLinkBelongsToCompletedSaleBranchAndNeverAnotherTenant() throws Exception {
  var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
  jdbc.execute("create table branch(branch_id bigint primary key,tenant_id bigint,activo boolean)");
  try(var connection=source.getConnection()){var script=new ClassPathResource("db/manual/20261104_google_review_links.sql");ScriptUtils.executeSqlScript(connection,script);ScriptUtils.executeSqlScript(connection,script);}
  jdbc.execute("create table sale(sale_id bigint primary key,tenant_id bigint,customer_id bigint,branch_id bigint,appointment_id bigint,user_id bigint,fecha_creacion timestamp,payment_validation_status varchar(30))");
  jdbc.execute("create table sale_item(sale_id bigint,service_id bigint,barber_user_id bigint,subtotal decimal)");jdbc.execute("create table service(service_id bigint,nombre varchar)");jdbc.execute("create table app_user(user_id bigint,nombre varchar,apellido varchar,photo_url varchar)");
  jdbc.execute("create table loyalty_movement(tenant_id bigint,customer_id bigint,referencia_id bigint,puntos integer,tipo varchar,origen varchar)");jdbc.execute("create table verified_business_review(tenant_id bigint,sale_id bigint,appointment_id bigint)");
  jdbc.update("insert into branch(branch_id,tenant_id,activo,google_review_url) values(10,1,true,'https://g.page/r/ONE/review'),(20,2,true,'https://g.page/r/TWO/review')");
  jdbc.update("insert into sale values(1,1,3,10,null,null,CURRENT_TIMESTAMP,'APPROVED'),(2,1,3,20,null,null,CURRENT_TIMESTAMP,'APPROVED'),(3,1,3,10,null,null,CURRENT_TIMESTAMP,'PENDING_VALIDATION'),(4,2,3,20,null,null,CURRENT_TIMESTAMP,'APPROVED')");
  String sql=AppointmentRepository.class.getMethod("findLastVisits",Long.class,Long.class,int.class).getAnnotation(Query.class).value();
  var rows=new NamedParameterJdbcTemplate(source).queryForList(sql,Map.of("tenantId",1L,"customerId",3L,"limit",5));
  assertEquals(2,rows.size());assertEquals("https://g.page/r/ONE/review",rows.stream().filter(r->((Number)r.get("saleId")).longValue()==1).findFirst().orElseThrow().get("googleReviewUrl"));
  assertNull(rows.stream().filter(r->((Number)r.get("saleId")).longValue()==2).findFirst().orElseThrow().get("googleReviewUrl"));
 }
}
