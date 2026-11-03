package com.gods.saas.web.controller;
import com.gods.saas.service.impl.WhatsappOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class WhatsappOnboardingController {
    private final WhatsappOnboardingService service;
    public record Start(String phone) {}
    public record Claim(String ticket) { @Override public String toString(){return "Onboarding claim [redacted]";} }
    public record Complete(String code,String wabaId,String phoneNumberId) { @Override public String toString(){return "Meta authorization [redacted]";} }
    @GetMapping("/api/owner/whatsapp-settings/onboarding")
    public ResponseEntity<?> information(@RequestAttribute("tenantId") Long tenant,@RequestAttribute("userId") Long user) {return response(service.information(tenant,user));}
    @PostMapping("/api/owner/whatsapp-settings/onboarding/start")
    public ResponseEntity<?> start(@RequestAttribute("tenantId") Long tenant,@RequestAttribute("userId") Long user,@RequestBody Start body) {return response(service.start(tenant,user,body.phone()));}
    @PostMapping("/api/owner/whatsapp-settings/onboarding/refresh")
    public ResponseEntity<?> refresh(@RequestAttribute("tenantId") Long tenant,@RequestAttribute("userId") Long user) {return response(service.refreshForOwner(tenant,user));}
    @PostMapping("/api/public/whatsapp/onboarding/claim")
    public ResponseEntity<?> claim(@RequestBody Claim body) {return response(service.claim(body.ticket()));}
    @GetMapping("/api/public/whatsapp/onboarding/status")
    public ResponseEntity<?> status(@RequestHeader("X-Whatsapp-Onboarding-Token") String token) {return response(service.sessionStatus(token));}
    @PostMapping("/api/public/whatsapp/onboarding/complete")
    public ResponseEntity<?> complete(@RequestHeader("X-Whatsapp-Onboarding-Token") String token,@RequestBody Complete body) {return response(service.complete(token,body.code(),body.wabaId(),body.phoneNumberId()));}
    private ResponseEntity<?> response(Map<String,Object> data) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(data);}
}
