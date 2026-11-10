package com.gods.saas.service.impl;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class WaitlistNotificationPolicyTest {
 @Test void onlyLiveOwnedPushInvitationsCanBeSent(){
 var repo=mock(BookingWaitlistRepository.class);
 var p=new TransactionalWhatsappPolicy(mock(AppointmentRepository.class),mock(SaleRepository.class),mock(TenantSettingsRepository.class),mock(TenantTimeService.class),repo);
 ReflectionTestUtils.setField(p,"waitlistEnabled",true);
 var n=Notification.builder().tenant(Tenant.builder().id(1L).build()).customer(Customer.builder().id(2L).build()).type(NotificationType.WAITLIST_AVAILABLE).referenceId(3L).build();
 var e=new BookingWaitlistEntry();e.setTenantId(1L);e.setCustomerId(2L);e.setStatus("AVAILABLE");e.setOfferExpiresAt(Instant.now().plusSeconds(30));when(repo.findById(3L)).thenReturn(Optional.of(e));
 assertTrue(p.current(n,false));assertFalse(p.current(n,true));
 e.setStatus("CANCELLED");assertFalse(p.current(n,false));e.setStatus("AVAILABLE");e.setOfferExpiresAt(Instant.now().minusSeconds(1));assertFalse(p.current(n,false));
 e.setOfferExpiresAt(Instant.now().plusSeconds(30));e.setCustomerId(9L);assertFalse(p.current(n,false));
 }
}
