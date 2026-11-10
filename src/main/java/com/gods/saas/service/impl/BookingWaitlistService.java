package com.gods.saas.service.impl;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.dto.request.JoinBookingWaitlistRequest;
import com.gods.saas.domain.dto.response.BookingWaitlistResponse;
import com.gods.saas.domain.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class BookingWaitlistService {
 private final BookingWaitlistRepository entries;
 private final CustomerRepository customers;
 private final BranchRepository branches;
 private final ServiceRepository services;
 private final ClientBookingService booking;
 private final TenantTimeService time;
 private final NotificationRepository notifications;
 private final NotificationDeliveryRepository deliveries;
 @Value("${booking.waitlist.enabled:false}") private boolean enabled;
 static final List<String> OPEN=List.of("WAITING","AVAILABLE");
 private void requireEnabled(){if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"La lista de espera aun no esta habilitada en este negocio.");}
 public BookingWaitlistResponse join(Long tenantId,Long customerId,JoinBookingWaitlistRequest r){
  requireEnabled();
  if(r==null||r.getBranchId()==null||r.getServiceId()==null||r.getDate()==null||r.getEarliestTime()==null||r.getLatestTime()==null)
   throw bad("Completa sede, servicio, fecha y franja horaria.");
  var today=time.today(tenantId);
  if(r.getDate().isBefore(today)||r.getDate().isAfter(today.plusDays(30))||!r.getEarliestTime().isBefore(r.getLatestTime()))throw bad("Elige una fecha de los proximos 30 dias y una franja valida.");
  if(!r.getDate().atTime(r.getLatestTime()).isAfter(time.now(tenantId)))throw bad("La franja elegida ya termino. Elige un horario futuro.");
  if(r.getBarberId()!=null&&r.getBarberId()<=0)throw bad("Profesional invalido.");
  var customer=customers.findForWhatsappRecovery(customerId,tenantId).orElseThrow(()->bad("Cliente no disponible."));
  if(!Boolean.TRUE.equals(customer.getActivo()))throw bad("Cliente inactivo.");
  var branch=branches.findByIdAndTenant_Id(r.getBranchId(),tenantId).orElseThrow(()->bad("Sede no disponible."));
  if(!Boolean.TRUE.equals(branch.getActivo()))throw bad("Sede inactiva.");
  var service=services.findByIdAndTenant_IdAndDeletedAtIsNull(r.getServiceId(),tenantId).orElseThrow(()->bad("Servicio no disponible."));
  if(!Boolean.TRUE.equals(service.getActivo()))throw bad("Servicio inactivo.");
  // Reuse authoritative duration, professional/branch/service and schedule rules.
  booking.getAvailability(tenantId,r.getBranchId(),r.getServiceId(),r.getDate().toString(),r.getBarberId());
  for(var e:entries.findByTenantIdAndCustomerIdAndStatusIn(tenantId,customerId,OPEN)){
   if(OPEN.contains(e.getStatus())&&Objects.equals(e.getBranchId(),r.getBranchId())&&Objects.equals(e.getServiceId(),r.getServiceId())&&Objects.equals(e.getBarberId(),r.getBarberId())&&e.getRequestedDate().equals(r.getDate())&&e.getEarliestTime().equals(r.getEarliestTime())&&e.getLatestTime().equals(r.getLatestTime()))return response(e);
  }
  if(entries.countByTenantIdAndCustomerIdAndStatusIn(tenantId,customerId,OPEN)>=5)throw bad("Puedes mantener hasta cinco solicitudes activas. Retira una para agregar otra.");
  var e=new BookingWaitlistEntry();e.setTenantId(tenantId);e.setCustomerId(customerId);e.setBranchId(r.getBranchId());e.setServiceId(r.getServiceId());e.setBarberId(r.getBarberId());e.setRequestedDate(r.getDate());e.setEarliestTime(r.getEarliestTime());e.setLatestTime(r.getLatestTime());e.setStatus("WAITING");e.setCreatedAt(Instant.now());e.setUpdatedAt(Instant.now());e.setCheckedAt(Instant.EPOCH);
  return response(entries.save(e));
 }
 public List<BookingWaitlistResponse> mine(Long tenantId,Long customerId){requireEnabled();return entries.findTop100ByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId,customerId).stream().map(this::response).toList();}
 public List<BookingWaitlistResponse> owner(Long tenantId,Long branchId){requireEnabled();if(branches.findByIdAndTenant_Id(branchId,tenantId).isEmpty())throw bad("Sede no disponible.");return entries.findTop200ByTenantIdAndBranchIdAndStatusInOrderByCreatedAtAsc(tenantId,branchId,OPEN).stream().map(this::response).toList();}
 public void cancel(Long tenantId,Long customerId,Long id){requireEnabled();var e=entries.lockById(id).filter(x->x.getTenantId().equals(tenantId)&&x.getCustomerId().equals(customerId)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(OPEN.contains(e.getStatus())){e.setStatus("CANCELLED");e.setUpdatedAt(Instant.now());}}
 public List<Long> dueIds(){if(!enabled)return List.of();return entries.findTop100ByStatusInOrderByCheckedAtAscCreatedAtAsc(OPEN).stream().map(BookingWaitlistEntry::getId).toList();}
 public void deferFailed(Long id){if(!enabled)return;entries.lockById(id).ifPresent(e->{e.setCheckedAt(Instant.now());});}
 public void refresh(Long id){
  if(!enabled)return;
  var e=entries.lockById(id).orElse(null);if(e==null||!OPEN.contains(e.getStatus()))return;
  var now=time.now(e.getTenantId());var instant=Instant.now();e.setCheckedAt(instant);e.setUpdatedAt(instant);
  if(now.isAfter(e.getRequestedDate().atTime(e.getLatestTime()))){e.setStatus("EXPIRED");return;}
  if("AVAILABLE".equals(e.getStatus())){
   if(e.getOfferExpiresAt().isAfter(instant))return;
   // One invitation per request: avoid repeated notifications for the same opening.
   e.setStatus("EXPIRED");return;
  }
  var available=booking.getAvailability(e.getTenantId(),e.getBranchId(),e.getServiceId(),e.getRequestedDate().toString(),e.getBarberId());
  var currentService=services.findByIdAndTenant_IdAndDeletedAtIsNull(e.getServiceId(),e.getTenantId()).orElseThrow();
  int duration=currentService.getDuracionMinutos()==null?30:currentService.getDuracionMinutos();
  var slot=available.getSlots().stream().map(LocalTime::parse).filter(t->!t.isBefore(e.getEarliestTime())&&!t.isAfter(e.getLatestTime())&&e.getRequestedDate().atTime(t).isAfter(now.plusMinutes(10))&&!e.getRequestedDate().atTime(t).plusMinutes(duration).isAfter(e.getRequestedDate().atTime(e.getLatestTime()))).findFirst();
  if(slot.isEmpty())return;
  var customer=customers.findByIdAndTenantId(e.getCustomerId(),e.getTenantId()).filter(c->Boolean.TRUE.equals(c.getActivo())).orElse(null);
  if(customer==null){e.setStatus("CANCELLED");return;}
  e.setStatus("AVAILABLE");e.setOfferedTime(slot.get());e.setOfferExpiresAt(instant.plusSeconds(600));
  var branch=branches.findByIdAndTenant_Id(e.getBranchId(),e.getTenantId()).orElseThrow();
  var n=notifications.save(Notification.builder().tenant(customer.getTenant()).branch(branch).customer(customer).type(NotificationType.WAITLIST_AVAILABLE).title("Hay un horario para ti").message("Encontramos disponibilidad en "+branch.getNombre()+" el "+e.getRequestedDate()+" a las "+slot.get()+". Abre tu lista de espera y revisa el horario. No esta reservado; confirma en la agenda.").referenceType("BOOKING_WAITLIST").referenceId(e.getId()).isRead(false).createdAt(LocalDateTime.now()).build());
  deliveries.save(NotificationDelivery.builder().notification(n).channel(NotificationChannel.PUSH).status(NotificationDeliveryStatus.PENDING).attempts(0).build());
 }
 private BookingWaitlistResponse response(BookingWaitlistEntry e){
  var b=branches.findByIdAndTenant_Id(e.getBranchId(),e.getTenantId()).map(Branch::getNombre).orElse("Sede no disponible");
  var s=services.findByIdAndTenant_Id(e.getServiceId(),e.getTenantId()).map(ServiceEntity::getNombre).orElse("Servicio no disponible");
  var c=customers.findByIdAndTenantId(e.getCustomerId(),e.getTenantId()).map(Customer::getNombres).orElse("Cliente");
  var status=e.getStatus();if("AVAILABLE".equals(status)&&e.getOfferExpiresAt()!=null&&!e.getOfferExpiresAt().isAfter(Instant.now()))status="EXPIRED";
  return new BookingWaitlistResponse(e.getId(),e.getBranchId(),b,e.getServiceId(),s,e.getBarberId(),e.getRequestedDate(),e.getEarliestTime(),e.getLatestTime(),status,e.getOfferedTime(),e.getOfferExpiresAt(),e.getCustomerId(),c);
 }
 private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
