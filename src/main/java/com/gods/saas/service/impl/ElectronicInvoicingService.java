package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gods.saas.domain.dto.request.IssueElectronicDocumentRequest;
import com.gods.saas.domain.dto.request.UpdateElectronicInvoicingSettingsRequest;
import com.gods.saas.domain.dto.response.ElectronicDocumentResponse;
import com.gods.saas.domain.dto.response.ElectronicDocumentFilesResponse;
import com.gods.saas.domain.dto.response.ElectronicInvoicingSettingsResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.invoicing.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class ElectronicInvoicingService {
    private final ElectronicInvoicingSettingsRepository settingsRepository;
    private final ElectronicDocumentRepository documentRepository;
    private final TenantRepository tenantRepository;
    private final SaleRepository saleRepository;
    private final MifactCredentialResolver credentials;
    private final ElectronicInvoiceProvider provider;
    private final MifactSalePayloadFactory payloadFactory;
    private final ObjectMapper mapper;

    public ElectronicInvoicingService(ElectronicInvoicingSettingsRepository settingsRepository,
            ElectronicDocumentRepository documentRepository, TenantRepository tenantRepository,
            SaleRepository saleRepository, MifactCredentialResolver credentials,
            ElectronicInvoiceProvider provider, MifactSalePayloadFactory payloadFactory, ObjectMapper mapper) {
        this.settingsRepository = settingsRepository; this.documentRepository = documentRepository;
        this.tenantRepository = tenantRepository; this.saleRepository = saleRepository;
        this.credentials = credentials; this.provider = provider;
        this.payloadFactory = payloadFactory; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ElectronicInvoicingSettingsResponse getSettings(Long tenantId) {
        return settingsRepository.findByTenantId(tenantId).map(this::settingsResponse).orElse(null);
    }

    @Transactional
    public ElectronicInvoicingSettingsResponse updateSettings(Long tenantId, UpdateElectronicInvoicingSettingsRequest r) {
        validateSettings(r);
        ElectronicInvoicingSettings s = settingsRepository.findLockedByTenantId(tenantId).orElseGet(() -> {
            ElectronicInvoicingSettings created = new ElectronicInvoicingSettings();
            created.setTenant(tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado")));
            return created;
        });
        s.setFiscalRuc(r.fiscalRuc().trim()); s.setLegalName(r.legalName().trim());
        s.setCommercialName(trim(r.commercialName())); s.setFiscalAddress(r.fiscalAddress().trim());
        s.setUbigeo(r.ubigeo().trim()); s.setSalesPointCode(r.salesPointCode().trim());
        s.setAnnexCode(normal(r.annexCode(), "0000")); s.setInvoiceSeries(r.invoiceSeries().trim().toUpperCase());
        s.setReceiptSeries(r.receiptSeries().trim().toUpperCase()); s.setCredentialAlias(r.credentialAlias().trim());
        if (r.nextInvoiceNumber() != null) s.setNextInvoiceNumber(r.nextInvoiceNumber());
        if (r.nextReceiptNumber() != null) s.setNextReceiptNumber(r.nextReceiptNumber());
        s.setIgvRate(r.igvRate()); s.setEnabled(r.enabled()); s.setUpdatedAt(LocalDateTime.now());
        if (r.enabled()) credentials.resolveToken(r.credentialAlias());
        return settingsResponse(settingsRepository.save(s));
    }

    @Transactional
    public ElectronicDocumentResponse issue(Long tenantId, Long saleId, IssueElectronicDocumentRequest request) {
        ElectronicDocument existing = documentRepository.findByTenantIdAndSaleIdAndDocumentType(tenantId, saleId, request.documentType()).orElse(null);
        if (existing != null) return documentResponse(existing);
        ElectronicInvoicingSettings settings = settingsRepository.findLockedByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("Configura la facturacion electronica primero"));
        if (!settings.isEnabled()) throw new IllegalStateException("La facturacion electronica del negocio esta deshabilitada");
        Sale sale = saleRepository.findForDeleteWithItems(saleId, tenantId, request.branchId())
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada en la sede"));
        if (!"APPROVED".equalsIgnoreCase(normal(sale.getPaymentValidationStatus(), "APPROVED")))
            throw new IllegalStateException("Solo se emiten comprobantes de ventas aprobadas");
        String token = credentials.resolveToken(settings.getCredentialAlias());
        String series; long sequence;
        if (request.documentType() == ElectronicDocumentType.INVOICE) {
            series = settings.getInvoiceSeries(); sequence = settings.getNextInvoiceNumber(); settings.setNextInvoiceNumber(sequence + 1);
        } else if (request.documentType() == ElectronicDocumentType.RECEIPT) {
            series = settings.getReceiptSeries(); sequence = settings.getNextReceiptNumber(); settings.setNextReceiptNumber(sequence + 1);
        } else throw new IllegalArgumentException("El piloto solo emite boleta o factura");
        ObjectNode payload = payloadFactory.build(sale, settings, request, token, series, sequence);
        ObjectNode safePayload = payload.deepCopy(); safePayload.remove("TOKEN");
        ElectronicDocument document = new ElectronicDocument();
        document.setTenant(sale.getTenant()); document.setBranch(sale.getBranch()); document.setSale(sale);
        document.setDocumentType(request.documentType()); document.setStatus(ElectronicDocumentStatus.PENDING);
        document.setSeries(series); document.setSequence(String.format("%08d", sequence));
        document.setRequestHash(sha256(safePayload.toString())); document.setRequestSnapshot(toMap(safePayload));
        document = documentRepository.saveAndFlush(document);
        settingsRepository.save(settings);
        try {
            document.setStatus(ElectronicDocumentStatus.PROCESSING);
            document.setAttemptCount(document.getAttemptCount() + 1); document.setLastAttemptAt(LocalDateTime.now());
            ProviderDocumentResponse response = provider.issue(payload);
            apply(document, response);
        } catch (RuntimeException ex) {
            document.setStatus(ElectronicDocumentStatus.ERROR);
            document.setErrorMessage(safeMessage(ex));
        }
        document.setUpdatedAt(LocalDateTime.now());
        return documentResponse(documentRepository.save(document));
    }

    @Transactional
    public ElectronicDocumentResponse refresh(Long tenantId, Long documentId) {
        ElectronicDocument document = documentRepository.findById(documentId)
                .filter(d -> d.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado"));
        ElectronicInvoicingSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("Configuracion fiscal no encontrada"));
        ObjectNode payload = mapper.createObjectNode();
        payload.put("TOKEN", credentials.resolveToken(settings.getCredentialAlias()));
        payload.put("NUM_NIF_EMIS", settings.getFiscalRuc());
        payload.put("COD_TIP_CPE", document.getDocumentType().getSunatCode());
        payload.put("NUM_SERIE_CPE", document.getSeries()); payload.put("NUM_CORRE_CPE", document.getSequence());
        document.setAttemptCount(document.getAttemptCount() + 1); document.setLastAttemptAt(LocalDateTime.now());
        try { apply(document, provider.getStatus(payload)); }
        catch (RuntimeException ex) { document.setErrorMessage(safeMessage(ex)); }
        document.setUpdatedAt(LocalDateTime.now());
        return documentResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public ElectronicDocumentFilesResponse getFiles(Long tenantId, Long documentId) {
        ElectronicDocument document = documentRepository.findById(documentId)
                .filter(d -> d.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado"));
        ElectronicInvoicingSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("Configuracion fiscal no encontrada"));
        ObjectNode payload = documentLookupPayload(settings, document);
        ProviderDocumentResponse response = provider.getDocument(payload);
        return new ElectronicDocumentFilesResponse(
                document.getId(), document.getSeries(), document.getSequence(),
                response.pdfBase64(), response.xmlBase64(), response.cdrBase64(),
                response.documentUrl());
    }

    private ObjectNode documentLookupPayload(ElectronicInvoicingSettings settings, ElectronicDocument document) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("TOKEN", credentials.resolveToken(settings.getCredentialAlias()));
        payload.put("NUM_NIF_EMIS", settings.getFiscalRuc());
        payload.put("COD_TIP_CPE", document.getDocumentType().getSunatCode());
        payload.put("NUM_SERIE_CPE", document.getSeries());
        payload.put("NUM_CORRE_CPE", document.getSequence());
        return payload;
    }

    @Transactional(readOnly = true)
    public List<ElectronicDocumentResponse> listBySale(Long tenantId, Long saleId) {
        return documentRepository.findByTenantIdAndSaleIdOrderByCreatedAtDesc(tenantId, saleId).stream().map(this::documentResponse).toList();
    }

    private void apply(ElectronicDocument d, ProviderDocumentResponse r) {
        d.setStatus(r.status()); d.setProviderStatus(r.providerStatus());
        if (r.series() != null) d.setSeries(r.series()); if (r.sequence() != null) d.setSequence(r.sequence());
        d.setSunatResponseCode(r.sunatResponseCode()); d.setSunatDescription(r.sunatDescription());
        d.setErrorMessage(r.errors()); d.setDocumentUrl(r.documentUrl());
        ObjectNode safe = r.rawResponse().deepCopy(); safe.remove(List.of("pdf_bytes", "xml_enviado", "cdr_sunat"));
        d.setResponseSnapshot(toMap(safe));
        if (r.status() == ElectronicDocumentStatus.ACCEPTED || r.status() == ElectronicDocumentStatus.ACCEPTED_WITH_OBSERVATIONS)
            d.setAcceptedAt(LocalDateTime.now());
    }
    private Map<String,Object> toMap(JsonNode node) { return mapper.convertValue(node, new TypeReference<>() {}); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String safeMessage(Exception e) { String m = e.getMessage(); return m == null ? e.getClass().getSimpleName() : m.substring(0, Math.min(m.length(), 2900)); }
    private void validateSettings(UpdateElectronicInvoicingSettingsRequest r) {
        if (r == null || r.fiscalRuc() == null || !r.fiscalRuc().matches("\\d{11}")) throw new IllegalArgumentException("RUC emisor invalido");
        if (blank(r.legalName()) || blank(r.fiscalAddress()) || r.ubigeo() == null || !r.ubigeo().matches("\\d{6}")) throw new IllegalArgumentException("Razon social, direccion y ubigeo son obligatorios");
        if (blank(r.salesPointCode()) || r.invoiceSeries() == null || !r.invoiceSeries().matches("(?i)F[A-Z0-9]{3}") || r.receiptSeries() == null || !r.receiptSeries().matches("(?i)B[A-Z0-9]{3}")) throw new IllegalArgumentException("Punto de venta y series F/B validas son obligatorios");
        if (blank(r.credentialAlias()) || r.igvRate() == null || r.igvRate().signum() < 0) throw new IllegalArgumentException("Alias e IGV son obligatorios");
        if ((r.nextInvoiceNumber() != null && r.nextInvoiceNumber() < 1) || (r.nextReceiptNumber() != null && r.nextReceiptNumber() < 1))
            throw new IllegalArgumentException("Los siguientes correlativos deben ser mayores a cero");
    }
    private boolean blank(String v) { return v == null || v.isBlank(); }
    private String trim(String v) { return v == null ? null : v.trim(); }
    private String normal(String v, String fallback) { return blank(v) ? fallback : v.trim(); }
    private ElectronicInvoicingSettingsResponse settingsResponse(ElectronicInvoicingSettings s) {
        boolean configured; try { credentials.resolveToken(s.getCredentialAlias()); configured = true; } catch (RuntimeException e) { configured = false; }
        return new ElectronicInvoicingSettingsResponse(s.getFiscalRuc(), s.getLegalName(), s.getCommercialName(), s.getFiscalAddress(), s.getUbigeo(), s.getSalesPointCode(), s.getAnnexCode(), s.getInvoiceSeries(), s.getReceiptSeries(), s.getNextInvoiceNumber(), s.getNextReceiptNumber(), s.getCredentialAlias(), s.getIgvRate(), s.isEnabled(), configured);
    }
    private ElectronicDocumentResponse documentResponse(ElectronicDocument d) {
        return new ElectronicDocumentResponse(d.getId(), d.getSale().getId(), d.getBranch().getId(), d.getDocumentType(), d.getStatus(), d.getSeries(), d.getSequence(), d.getProviderStatus(), d.getSunatResponseCode(), d.getSunatDescription(), d.getErrorMessage(), d.getDocumentUrl(), d.getAttemptCount(), d.getLastAttemptAt(), d.getAcceptedAt(), d.getCreatedAt());
    }
}
