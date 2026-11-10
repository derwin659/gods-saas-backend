package com.gods.saas.web.controller;
import com.gods.saas.service.impl.ClientRebookingService;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/api/clients/booking/rebook")
public class ClientRebookingController {
 private final ClientRebookingService service;private final JwtUtil jwt;
 @GetMapping("/{appointmentId}") public Map<String,Object> seed(@RequestHeader("Authorization")String h,@PathVariable Long appointmentId){var t=h.replace("Bearer ","");return service.seed(jwt.getTenantIdFromToken(t),jwt.getCustomerIdFromToken(t),appointmentId);}
}
