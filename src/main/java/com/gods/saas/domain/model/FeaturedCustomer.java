package com.gods.saas.domain.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name = "featured_customer")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeaturedCustomer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="featured_customer_id") private Long id;
 @Column(name="business_name",nullable=false,length=150) private String businessName;
 @Column(name="business_type",length=60) private String businessType;
 @Column(length=100) private String city;
 @Column(name="logo_url",nullable=false,length=500) private String logoUrl;
 @Column(name="logo_public_id",nullable=false,length=300) private String logoPublicId;
 @Column(length=300) private String website;
 @Column(length=350) private String testimonial;
 @Builder.Default @Column(nullable=false) private Boolean visible=true;
 @Builder.Default @Column(name="sort_order",nullable=false) private Integer sortOrder=0;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @PrePersist void onCreate(){createdAt=updatedAt=LocalDateTime.now();}
 @PreUpdate void onUpdate(){updatedAt=LocalDateTime.now();}
}