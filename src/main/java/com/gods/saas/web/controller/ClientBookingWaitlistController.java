package com.gods.saas.web.controller;
import com.gods.saas.domain.dto.request.JoinBookingWaitlistRequest;
import com.gods.saas.domain.dto.response.BookingWaitlistResponse;
import com.gods.saas.service.impl.BookingWaitlistService;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/clients/booking/waitlist")
public class ClientBookingWaitlistController {
 private final BookingWaitlistService service;private final JwtUtil jwt;
 @GetMapping public List<BookingWaitlistResponse> mine(@RequestHeader("Authorization") String h){var t=token(h);return service.mine(jwt.getTenantIdFromToken(t),jwt.getCustomerIdFromToken(t));}
 @PostMapping public BookingWaitlistResponse join(@RequestHeader("Authorization") String h,@RequestBody JoinBookingWaitlistRequest r){var t=token(h);return service.join(jwt.getTenantIdFromToken(t),jwt.getCustomerIdFromToken(t),r);}
 @DeleteMapping("/{id}") public void cancel(@RequestHeader("Authorization") String h,@PathVariable Long id){var t=token(h);service.cancel(jwt.getTenantIdFromToken(t),jwt.getCustomerIdFromToken(t),id);}
 private String token(String h){return h.replace("Bearer ","");}
}
