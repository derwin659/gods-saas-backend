package com.gods.saas.service.impl;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ClientRebookingService {
 private final AppointmentRepository appointments;
 public Map<String,Object> seed(Long tenantId,Long customerId,Long appointmentId){
  var a=appointments.findByIdAndTenant_IdAndCustomer_Id(appointmentId,tenantId,customerId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Visita no disponible."));
  // Revalidate the current catalogue: old visits never resurrect a retired service.
  if(a.getService()==null||!Boolean.TRUE.equals(a.getService().getActivo())||a.getService().getDeletedAt()!=null||!Boolean.TRUE.equals(a.getBranch().getActivo()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Este servicio o sede ya no esta disponible. Elige una nueva atencion desde Reservar.");
  var result=new HashMap<String,Object>();result.put("branchId",a.getBranch().getId());result.put("serviceId",a.getService().getId());
  if(a.getUser()!=null)result.put("barberId",a.getUser().getId());
  return result;
 }
}
