package com.gods.saas.web.controller;
import com.gods.saas.domain.dto.response.FeaturedCustomerResponse;
import com.gods.saas.service.impl.FeaturedCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
@RestController @RequiredArgsConstructor
public class FeaturedCustomerController {
 private final FeaturedCustomerService service;
 @GetMapping("/api/public/featured-customers") public List<FeaturedCustomerResponse> publicList(){return service.publicList();}
 @GetMapping("/api/super-admin/featured-customers") @PreAuthorize("hasRole('SUPER_ADMIN')") public List<FeaturedCustomerResponse> adminList(){return service.adminList();}
 @PostMapping(value="/api/super-admin/featured-customers",consumes="multipart/form-data") @PreAuthorize("hasRole('SUPER_ADMIN')")
 public FeaturedCustomerResponse create(@RequestParam String businessName,@RequestParam(required=false)String businessType,@RequestParam(required=false)String city,@RequestParam(required=false)String website,@RequestParam(required=false)String testimonial,@RequestParam(defaultValue="0")Integer sortOrder,@RequestParam(defaultValue="true")Boolean visible,@RequestPart("logo")MultipartFile logo){return service.create(businessName,businessType,city,website,testimonial,sortOrder,visible,logo);}
 @PutMapping(value="/api/super-admin/featured-customers/{id}",consumes="multipart/form-data") @PreAuthorize("hasRole('SUPER_ADMIN')")
 public FeaturedCustomerResponse update(@PathVariable Long id,@RequestParam String businessName,@RequestParam(required=false)String businessType,@RequestParam(required=false)String city,@RequestParam(required=false)String website,@RequestParam(required=false)String testimonial,@RequestParam(defaultValue="0")Integer sortOrder,@RequestParam(defaultValue="true")Boolean visible,@RequestPart(value="logo",required=false)MultipartFile logo){return service.update(id,businessName,businessType,city,website,testimonial,sortOrder,visible,logo);}
 @DeleteMapping("/api/super-admin/featured-customers/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')") public void delete(@PathVariable Long id){service.delete(id);}
}