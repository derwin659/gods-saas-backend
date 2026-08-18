package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "electronic_invoicing_settings")
@Getter @Setter @NoArgsConstructor
public class ElectronicInvoicingSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;
    @Column(name = "fiscal_ruc", nullable = false, length = 11) private String fiscalRuc;
    @Column(name = "legal_name", nullable = false, length = 250) private String legalName;
    @Column(name = "commercial_name", length = 250) private String commercialName;
    @Column(name = "fiscal_address", nullable = false, length = 500) private String fiscalAddress;
    @Column(name = "ubigeo", nullable = false, length = 6) private String ubigeo;
    @Column(name = "sales_point_code", nullable = false, length = 20) private String salesPointCode;
    @Column(name = "annex_code", nullable = false, length = 4) private String annexCode = "0000";
    @Column(name = "invoice_series", nullable = false, length = 4) private String invoiceSeries = "F001";
    @Column(name = "receipt_series", nullable = false, length = 4) private String receiptSeries = "B001";
    @Column(name = "next_invoice_number", nullable = false) private long nextInvoiceNumber = 1;
    @Column(name = "next_receipt_number", nullable = false) private long nextReceiptNumber = 1;
    @Column(name = "credential_alias", nullable = false, length = 50) private String credentialAlias;
    @Column(name = "igv_rate", nullable = false, precision = 5, scale = 2) private BigDecimal igvRate = new BigDecimal("18.00");
    @Column(nullable = false) private boolean enabled;
    @Version private long version;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
}
