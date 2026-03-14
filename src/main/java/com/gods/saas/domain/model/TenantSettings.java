package com.gods.saas.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "tenant")
public class TenantSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    private String language = "es";  // es, en, pt
    private String timezone = "America/Lima";
    private String currency = "PEN";
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> scheduleConfig = new HashMap<>();
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}

