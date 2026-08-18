package com.gods.saas.domain.model;

import com.gods.saas.invoicing.ElectronicDocumentStatus;
import com.gods.saas.invoicing.ElectronicDocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "electronic_document")
@Getter @Setter @NoArgsConstructor
public class ElectronicDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private ElectronicDocumentType documentType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ElectronicDocumentStatus status = ElectronicDocumentStatus.DRAFT;

    @Column(nullable = false, length = 30) private String provider = "MIFACT";
    @Column(length = 10) private String series;
    @Column(length = 20) private String sequence;
    @Column(name = "provider_status", length = 10) private String providerStatus;
    @Column(name = "sunat_response_code", length = 30) private String sunatResponseCode;
    @Column(name = "sunat_description", length = 2000) private String sunatDescription;
    @Column(name = "error_message", length = 3000) private String errorMessage;
    @Column(name = "document_url", length = 1000) private String documentUrl;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_snapshot", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> requestSnapshot = new HashMap<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> responseSnapshot = new HashMap<>();

    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "last_attempt_at") private LocalDateTime lastAttemptAt;
    @Column(name = "accepted_at") private LocalDateTime acceptedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
}
