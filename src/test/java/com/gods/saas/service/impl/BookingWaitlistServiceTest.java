package com.gods.saas.service.impl;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.dto.response.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BookingWaitlistServiceTest {
 @Mock BookingWaitlistRepository entries; @Mock CustomerRepository customers; @Mock BranchRepository branches;
 @Mock ServiceRepository services; @Mock ClientBookingService booking; @Mock TenantTimeService time;
 @Mock NotificationRepository notifications; @Mock NotificationDeliveryRepository deliveries;
 @InjectMocks BookingWaitlistService service;
 AutoCloseable mocks;
 final LocalDate date=LocalDate.of(2026,11,10);
 @BeforeEach void setup(){mocks=MockitoAnnotations.openMocks(this);ReflectionTestUtils.setField(service,"enabled",true);when(time.today(1L)).thenReturn(date);when(time.now(1L)).thenReturn(date.atTime(8,0));}
 @AfterEach void close()throws Exception{mocks.close();}
 JoinBookingWaitlistRequest request(){var r=new JoinBookingWaitlistRequest();r.setBranchId(2L);r.setServiceId(3L);r.setDate(date);r.setEarliestTime(LocalTime.of(9,0));r.setLatestTime(LocalTime.of(12,0));return r;}
 BookingWaitlistEntry entry(){var e=new BookingWaitlistEntry();e.setId(7L);e.setTenantId(1L);e.setCustomerId(4L);e.setBranchId(2L);e.setServiceId(3L);e.setStatus("WAITING");e.setRequestedDate(date);e.setEarliestTime(LocalTime.of(9,0));e.setLatestTime(LocalTime.of(12,0));return e;}
 void catalog(){
 var c=new Customer();c.setId(4L);c.setActivo(true);c.setNombres("Cliente");c.setTenant(Tenant.builder().id(1L).build());
 var b=new Branch();b.setId(2L);b.setActivo(true);b.setNombre("Principal");
 var s=new ServiceEntity();s.setId(3L);s.setNombre("Corte");s.setActivo(true);s.setDuracionMinutos(30);
 when(customers.findForWhatsappRecovery(4L,1L)).thenReturn(Optional.of(c));when(customers.findByIdAndTenantId(4L,1L)).thenReturn(Optional.of(c));
 when(branches.findByIdAndTenant_Id(2L,1L)).thenReturn(Optional.of(b));
 when(services.findByIdAndTenant_IdAndDeletedAtIsNull(3L,1L)).thenReturn(Optional.of(s));when(services.findByIdAndTenant_Id(3L,1L)).thenReturn(Optional.of(s));
 when(entries.findByTenantIdAndCustomerIdAndStatusIn(1L,4L,BookingWaitlistService.OPEN)).thenReturn(List.of());
 when(entries.save(any())).thenAnswer(i->{BookingWaitlistEntry e=i.getArgument(0);e.setId(7L);return e;});
 }
 @Test void createsWaitingRequestWithoutCreatingAppointment(){catalog();var r=service.join(1L,4L,request());assertEquals("WAITING",r.status());verify(booking).getAvailability(1L,2L,3L,date.toString(),null);verify(booking,never()).createAppointment(any(),any(),any());}
 @Test void repeatedSubmissionReturnsExistingRequest(){catalog();when(entries.findByTenantIdAndCustomerIdAndStatusIn(1L,4L,BookingWaitlistService.OPEN)).thenReturn(List.of(entry()));assertEquals(7L,service.join(1L,4L,request()).id());verify(entries,never()).save(any());}
 @Test void cannotWithdrawAnotherCustomersRequest(){when(entries.lockById(7L)).thenReturn(Optional.of(entry()));assertThrows(ResponseStatusException.class,()->service.cancel(1L,99L,7L));assertThrows(ResponseStatusException.class,()->service.cancel(99L,4L,7L));}
 @Test void oneOfferQueuesPushOnlyAndDoesNotReserve(){catalog();var e=entry();when(entries.lockById(7L)).thenReturn(Optional.of(e));when(booking.getAvailability(1L,2L,3L,date.toString(),null)).thenReturn(BookingAvailabilityResponse.builder().slots(List.of("08:00","11:45","10:00")).build());when(notifications.save(any())).thenAnswer(i->i.getArgument(0));service.refresh(7L);service.refresh(7L);assertEquals(LocalTime.of(10,0),e.getOfferedTime());assertEquals("AVAILABLE",e.getStatus());verify(deliveries,times(1)).save(any());verify(booking,never()).createAppointment(any(),any(),any());}
 @Test void expiredOfferIsNotNotifiedAgain(){var e=entry();e.setStatus("AVAILABLE");e.setOfferExpiresAt(Instant.now().minusSeconds(5));when(entries.lockById(7L)).thenReturn(Optional.of(e));service.refresh(7L);assertEquals("EXPIRED",e.getStatus());verifyNoInteractions(deliveries,booking);}
 @Test void disabledFeatureNeverQueriesMissingTable(){ReflectionTestUtils.setField(service,"enabled",false);assertEquals(List.of(),service.dueIds());assertThrows(ResponseStatusException.class,()->service.mine(1L,4L));verifyNoInteractions(entries);}
 @Test void rejectsInvalidDateWindowBeforeQuery(){var r=request();r.setDate(date.minusDays(1));assertThrows(ResponseStatusException.class,()->service.join(1L,4L,r));r.setDate(date);r.setLatestTime(r.getEarliestTime());assertThrows(ResponseStatusException.class,()->service.join(1L,4L,r));verifyNoInteractions(entries,customers);}
}
