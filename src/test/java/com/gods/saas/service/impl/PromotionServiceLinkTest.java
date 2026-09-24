package com.gods.saas.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.dto.request.PromotionRequest;
import com.gods.saas.domain.enums.PromotionRedirectType;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromotionServiceLinkTest {
 @Test void acceptsServicePayload() throws Exception {
   var request = new ObjectMapper().readValue("{\"redirectType\":\"SERVICE\",\"redirectValue\":\"42\"}", PromotionRequest.class);
   assertEquals(PromotionRedirectType.SERVICE, request.getRedirectType());
   assertEquals("42", request.getRedirectValue());
 }
 @Test void rejectsServiceFromAnotherBusiness() {
   var repository = mock(ServiceRepository.class);
   var target = new PromotionServiceImpl(null,null,null,null,repository,null,null,null,null);
   var tenant = new Tenant(); tenant.setId(5L);
   var promotion = new Promotion(); promotion.setTenant(tenant);
   promotion.setRedirectType(PromotionRedirectType.SERVICE); promotion.setRedirectValue("42");
   when(repository.findByIdAndTenant_IdAndDeletedAtIsNull(42L,5L)).thenReturn(Optional.empty());
   assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(target,"validatePromotion",promotion));
   verify(repository).findByIdAndTenant_IdAndDeletedAtIsNull(42L,5L);
 }
 @Test void acceptsActiveServiceAndRejectsInactiveAndMalformedLinks() {
   var repository = mock(ServiceRepository.class);
   var target = new PromotionServiceImpl(null,null,null,null,repository,null,null,null,null);
   var tenant = new Tenant(); tenant.setId(5L);
   var promotion = new Promotion(); promotion.setTenant(tenant);
   promotion.setTitulo("Corte y barba");
   promotion.setTipo(com.gods.saas.domain.enums.PromotionType.values()[0]);
   promotion.setRedirectType(PromotionRedirectType.SERVICE); promotion.setRedirectValue(" 42 ");
   var service = new ServiceEntity(); service.setActivo(true);
   when(repository.findByIdAndTenant_IdAndDeletedAtIsNull(42L,5L)).thenReturn(Optional.of(service));
   ReflectionTestUtils.invokeMethod(target,"validatePromotion",promotion);
   assertEquals("42",promotion.getRedirectValue());
   service.setActivo(false);
   assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(target,"validatePromotion",promotion));
   promotion.setRedirectValue("Corte y barba");
   assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(target,"validatePromotion",promotion));
 }
}
