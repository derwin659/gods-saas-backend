package com.gods.saas.web.controller;
import com.gods.saas.service.impl.*;
import com.gods.saas.security.BranchAccessGuard;
import com.gods.saas.domain.dto.response.BookingWaitlistResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/owner/booking-waitlist")
public class OwnerBookingWaitlistController {
 private final BookingWaitlistService service;private final AdminPermissionService permissions;private final BranchAccessGuard guard;
 @GetMapping public List<BookingWaitlistResponse> list(@RequestParam(required=false) Long branchId,Authentication auth){
  permissions.checkPermission("AGENDA_ACCESS");var d=(Map<?,?>)auth.getDetails();
  Long sessionBranch=d.get("branchId")==null?null:Long.valueOf(d.get("branchId").toString());
  return service.owner(Long.valueOf(d.get("tenantId").toString()),guard.resolve(branchId,sessionBranch));
 }
}
