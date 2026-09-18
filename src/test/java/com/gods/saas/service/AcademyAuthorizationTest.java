package com.gods.saas.service;
import com.gods.saas.service.impl.AcademyService;
import com.gods.saas.web.controller.AcademyController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AcademyAuthorizationTest {
 @Configuration static class Config {
   @Bean AcademyService service() { return mock(AcademyService.class); }
   @Bean AcademyController controller(AcademyService service) { return new AcademyController(service); }
 }
 @Test void onlySuperAdminCanManageLessons() {
   try(var context=new AnnotationConfigApplicationContext(Config.class)) {
     var controller=context.getBean(AcademyController.class);
     var service=context.getBean(AcademyService.class);
     for(String role:List.of("OWNER","ADMIN","CLIENT","BARBER","CASHIER")) {
       SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user",null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
       assertThrows(AccessDeniedException.class,controller::list);
       assertThrows(AccessDeniedException.class,() -> controller.create(null));
       assertThrows(AccessDeniedException.class,() -> controller.update("lesson",null));
       assertThrows(AccessDeniedException.class,() -> controller.upload("lesson",0,null));
       assertThrows(AccessDeniedException.class,() -> controller.publish("lesson",new AcademyService.Revision(0)));
       assertThrows(AccessDeniedException.class,() -> controller.unpublish("lesson",new AcademyService.Revision(0)));
     }
     verifyNoInteractions(service);
     SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("root",null,List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
     controller.list();verify(service).listAdmin();
   } finally { SecurityContextHolder.clearContext(); }
 }
}