package com.gods.saas.service.impl;
import com.gods.saas.domain.dto.response.FeaturedCustomerResponse;
import com.gods.saas.domain.model.FeaturedCustomer;
import com.gods.saas.domain.repository.FeaturedCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
@Service @RequiredArgsConstructor
public class FeaturedCustomerService {
 private final FeaturedCustomerRepository repository; private final CloudinaryStorageService storage;
 @Transactional(readOnly=true) public List<FeaturedCustomerResponse> publicList(){return repository.findByVisibleTrueOrderBySortOrderAscIdAsc().stream().map(FeaturedCustomerResponse::from).toList();}
 @Transactional(readOnly=true) public List<FeaturedCustomerResponse> adminList(){return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(FeaturedCustomerResponse::from).toList();}
 @Transactional public FeaturedCustomerResponse create(String name,String type,String city,String website,String testimonial,Integer order,Boolean visible,MultipartFile logo){
  var upload=storage.uploadFeaturedCustomerLogo(logo); var x=FeaturedCustomer.builder().businessName(required(name)).businessType(clean(type)).city(clean(city)).website(clean(website)).testimonial(clean(testimonial)).sortOrder(order==null?0:order).visible(visible==null||visible).logoUrl(upload.getSecureUrl()).logoPublicId(upload.getPublicId()).build(); return FeaturedCustomerResponse.from(repository.save(x));
 }
 @Transactional public FeaturedCustomerResponse update(Long id,String name,String type,String city,String website,String testimonial,Integer order,Boolean visible,MultipartFile logo){
  var x=get(id); x.setBusinessName(required(name));x.setBusinessType(clean(type));x.setCity(clean(city));x.setWebsite(clean(website));x.setTestimonial(clean(testimonial));x.setSortOrder(order==null?0:order);x.setVisible(visible==null||visible);
  if(logo!=null&&!logo.isEmpty()){var upload=storage.uploadFeaturedCustomerLogo(logo);String old=x.getLogoPublicId();x.setLogoUrl(upload.getSecureUrl());x.setLogoPublicId(upload.getPublicId());if(old!=null)storage.deleteImage(old);} return FeaturedCustomerResponse.from(repository.save(x));
 }
 @Transactional public void delete(Long id){var x=get(id);repository.delete(x);if(x.getLogoPublicId()!=null)storage.deleteImage(x.getLogoPublicId());}
 private FeaturedCustomer get(Long id){return repository.findById(id).orElseThrow(()->new IllegalArgumentException("Cliente destacado no encontrado"));}
 private String required(String v){String x=clean(v);if(x==null)throw new IllegalArgumentException("El nombre del negocio es obligatorio");return x;}
 private String clean(String v){return v==null||v.trim().isEmpty()?null:v.trim();}
}