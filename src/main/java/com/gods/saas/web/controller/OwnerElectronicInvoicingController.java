package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.IssueElectronicDocumentRequest;
import com.gods.saas.domain.dto.request.UpdateElectronicInvoicingSettingsRequest;
import com.gods.saas.domain.dto.response.ElectronicDocumentResponse;
import com.gods.saas.domain.dto.response.ElectronicDocumentFilesResponse;
import com.gods.saas.domain.dto.response.ElectronicInvoicingSettingsResponse;
import com.gods.saas.service.impl.ElectronicInvoicingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/owner/electronic-invoicing")
public class OwnerElectronicInvoicingController {
    private final ElectronicInvoicingService service;
    public OwnerElectronicInvoicingController(ElectronicInvoicingService service) { this.service = service; }

    @GetMapping("/settings")
    public ElectronicInvoicingSettingsResponse getSettings(HttpServletRequest request) { return service.getSettings(tenantId(request)); }
    @PutMapping("/settings")
    public ElectronicInvoicingSettingsResponse updateSettings(HttpServletRequest request, @RequestBody UpdateElectronicInvoicingSettingsRequest body) { return service.updateSettings(tenantId(request), body); }
    @PostMapping("/sales/{saleId}/issue")
    public ElectronicDocumentResponse issue(HttpServletRequest request, @PathVariable Long saleId, @RequestBody IssueElectronicDocumentRequest body) { return service.issue(tenantId(request), saleId, body); }
    @GetMapping("/sales/{saleId}")
    public List<ElectronicDocumentResponse> list(HttpServletRequest request, @PathVariable Long saleId) { return service.listBySale(tenantId(request), saleId); }
    @PostMapping("/documents/{documentId}/refresh")
    public ElectronicDocumentResponse refresh(HttpServletRequest request, @PathVariable Long documentId) { return service.refresh(tenantId(request), documentId); }
    @GetMapping("/documents/{documentId}/files")
    public ElectronicDocumentFilesResponse files(HttpServletRequest request, @PathVariable Long documentId) { return service.getFiles(tenantId(request), documentId); }

    private Long tenantId(HttpServletRequest request) {
        Object value = request.getAttribute("tenantId"); if (value == null) value = request.getAttribute("tenant_id");
        if (value == null) throw new IllegalStateException("No se pudo resolver tenantId desde la sesion");
        return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
    }
}
