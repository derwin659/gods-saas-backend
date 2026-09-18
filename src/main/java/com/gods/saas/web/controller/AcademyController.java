package com.gods.saas.web.controller;
import com.gods.saas.service.impl.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
@RestController @RequiredArgsConstructor
public class AcademyController {
 private final AcademyService service;
 @GetMapping("/api/academy") @PreAuthorize("hasAnyRole('OWNER','ADMIN','CLIENT','BARBER','CASHIER')")
 public ResponseEntity<AcademyService.Catalog> catalog(Authentication auth,@RequestParam(defaultValue="mobile") String platform) {
   String role=auth.getAuthorities().stream().map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).filter(AcademyService.ROLES::contains).findFirst().orElse("");
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.catalog(role,platform));
 }
 @GetMapping("/api/super-admin/academy") @PreAuthorize("hasRole('SUPER_ADMIN')")
 public List<AcademyService.AdminLesson> list() { requireSuperAdmin(); return service.listAdmin(); }
 @PostMapping("/api/super-admin/academy") @PreAuthorize("hasRole('SUPER_ADMIN')")
 public AcademyService.AdminLesson create(@RequestBody AcademyService.Draft input) { requireSuperAdmin(); return service.create(input); }
 @PutMapping("/api/super-admin/academy/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')")
 public AcademyService.AdminLesson update(@PathVariable String id,@RequestBody AcademyService.Draft input) { requireSuperAdmin(); return service.update(id,input); }
 @PostMapping(value="/api/super-admin/academy/{id}/video",consumes="multipart/form-data") @PreAuthorize("hasRole('SUPER_ADMIN')")
 public AcademyService.AdminLesson upload(@PathVariable String id,@RequestParam long version,@RequestPart("video") MultipartFile video) { requireSuperAdmin(); return service.upload(id,version,video); }
 @PostMapping("/api/super-admin/academy/{id}/publish") @PreAuthorize("hasRole('SUPER_ADMIN')")
 public AcademyService.AdminLesson publish(@PathVariable String id,@RequestBody AcademyService.Revision input) { requireSuperAdmin(); return service.publish(id,input.version()); }
 @PostMapping("/api/super-admin/academy/{id}/unpublish") @PreAuthorize("hasRole('SUPER_ADMIN')")
 public AcademyService.AdminLesson unpublish(@PathVariable String id,@RequestBody AcademyService.Revision input) { requireSuperAdmin(); return service.unpublish(id,input.version()); }
 private void requireSuperAdmin() {
   var auth=SecurityContextHolder.getContext().getAuthentication();
   if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")))
     throw new AccessDeniedException("Solo el superadministrador puede administrar Academy");
 }
}
