package com.gods.saas.service.impl;
import com.gods.saas.domain.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class OwnerBarberActivityService {
 private final SaleRepository sales;
 public Map<String,Object> counts(Long tenantId,Long branchId,Long barberId){ZoneId zone=ZoneId.of("America/Lima");LocalDate today=LocalDate.now(zone);LocalDateTime end=today.plusDays(1).atStartOfDay();long todayCount=count(tenantId,branchId,barberId,today.atStartOfDay(),end);long seven=count(tenantId,branchId,barberId,today.minusDays(6).atStartOfDay(),end);long month=count(tenantId,branchId,barberId,today.withDayOfMonth(1).atStartOfDay(),end);Map<String,Object> result=new LinkedHashMap<>();result.put("today",todayCount);result.put("last7Days",seven);result.put("thisMonth",month);result.put("asOf",today);return result;}
 private long count(Long t,Long b,Long u,LocalDateTime from,LocalDateTime to){return sales.countApprovedServicesByBarberRange(t,b,u,from,to);}
}
