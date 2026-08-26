package com.gods.saas.domain.dto.response;
import com.gods.saas.domain.model.FeaturedCustomer;
public record FeaturedCustomerResponse(Long id,String businessName,String businessType,String city,String logoUrl,String website,String testimonial,Boolean visible,Integer sortOrder){
 public static FeaturedCustomerResponse from(FeaturedCustomer x){return new FeaturedCustomerResponse(x.getId(),x.getBusinessName(),x.getBusinessType(),x.getCity(),x.getLogoUrl(),x.getWebsite(),x.getTestimonial(),x.getVisible(),x.getSortOrder());}
}