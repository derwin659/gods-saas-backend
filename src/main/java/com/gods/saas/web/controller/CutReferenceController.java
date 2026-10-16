package com.gods.saas.web.controller;
import com.gods.saas.service.impl.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController @RequestMapping("/api/cut-references/sales") @RequiredArgsConstructor
public class CutReferenceController {
    private final CutReferenceService service;
    private final AdminPermissionService permissions;
    private CutReferenceService.Actor actor(Authentication auth, boolean write) {
        if (auth == null || !(auth.getDetails() instanceof Map<?,?> claims)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String role=auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).findFirst().orElse("");
        if ("ADMIN".equals(role) || "CASHIER".equals(role)) permissions.checkPermission(write ? "CASH_REGISTER_INCOME" : "CUSTOMERS_ACCESS");
        Long tenant=number(claims.get("tenantId"));
        if (tenant == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return new CutReferenceService.Actor(tenant,number(claims.get("userId")),number(claims.get("customerId")),role);
    }
    private Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    @GetMapping("/{saleId}")
    public ResponseEntity<CutReferenceService.View> get(Authentication auth,@PathVariable Long saleId) {
        var result=service.get(actor(auth,false),saleId);
        return result==null ? ResponseEntity.noContent().build() : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
    @PostMapping(value="/{saleId}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public CutReferenceService.View upload(Authentication auth,@PathVariable Long saleId,@RequestParam("file") MultipartFile file) {
        return service.upload(actor(auth,true),saleId,file);
    }
}
