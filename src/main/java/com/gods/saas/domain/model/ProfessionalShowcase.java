package com.gods.saas.domain.model;
import com.gods.saas.domain.enums.ShowcaseStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="professional_showcase")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProfessionalShowcase {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="showcase_id") private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="tenant_id") private Tenant tenant;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="branch_id") private Branch branch;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="professional_user_id") private AppUser professional;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="service_id") private ServiceEntity service;
 @Column(nullable=false,length=120) private String title;
 @Column(length=600) private String description;
 @Column(name="media_type",nullable=false,length=20) private String mediaType;
 @Column(name="image_url",nullable=false,length=700) private String imageUrl;
 @Column(name="thumbnail_url",length=700) private String thumbnailUrl;
 @Column(name="image_public_id",length=500) private String imagePublicId;
 @Column(name="duration_seconds") private Integer durationSeconds;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ShowcaseStatus status;
 @Column(name="client_image_consent",nullable=false) private boolean clientImageConsent;
 @Column(name="rejection_reason",length=300) private String rejectionReason;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="moderated_by_user_id") private AppUser moderatedBy;
 @Column(name="moderated_at") private LocalDateTime moderatedAt;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @Column(name="published_at") private LocalDateTime publishedAt;
 @Column(name="archived_at") private LocalDateTime archivedAt;
 @PrePersist void create(){var now=LocalDateTime.now();if(status==null)status=ShowcaseStatus.PENDING_APPROVAL;if(mediaType==null)mediaType="IMAGE";if(createdAt==null)createdAt=now;updatedAt=now;}
 @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}