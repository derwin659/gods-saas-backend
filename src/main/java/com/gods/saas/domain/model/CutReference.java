package com.gods.saas.domain.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="cut_reference") @Getter @Setter @NoArgsConstructor
public class CutReference {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(name="sale_id",nullable=false,unique=true) private Long saleId;
    @Column(name="public_id",nullable=false) private String publicId;
    @Column(name="media_type",nullable=false) private String mediaType;
    @Column(nullable=false) private String format;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
}
