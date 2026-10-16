package com.gods.saas.service.impl;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class CutReferenceService {
    private final SaleRepository sales;
    private final CutReferenceRepository references;
    private final CloudinaryStorageService storage;
    public record Actor(Long tenantId, Long userId, Long customerId, String role) {}
    public record View(Long saleId, String mediaType, String url) {}

    Sale authorize(Actor actor, Long saleId, boolean write) {
        Sale sale = sales.findByIdAndTenant_Id(saleId, actor.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (sale.getCustomer() == null || sale.getItems() == null || sale.getItems().stream().noneMatch(i -> i.getService() != null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La venta no tiene cliente o servicios asociados");
        boolean allowed = switch (actor.role()) {
            case "OWNER", "ADMIN", "CASHIER" -> true;
            case "CUSTOMER" -> !write && actor.customerId() != null && Objects.equals(actor.customerId(),sale.getCustomer().getId());
            case "BARBER" -> actor.userId() != null && ((sale.getUser() != null && Objects.equals(actor.userId(),sale.getUser().getId()))
                    || sale.getItems().stream().anyMatch(i -> i.getBarberUser() != null && Objects.equals(actor.userId(),i.getBarberUser().getId())));
            default -> false;
        };
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return sale;
    }
    @Transactional(readOnly=true)
    public View get(Actor actor, Long saleId) {
        authorize(actor,saleId,false);
        return references.findByTenantIdAndSaleId(actor.tenantId(),saleId)
                .map(ref -> new View(saleId,ref.getMediaType(),storage.cutReferenceUrl(ref.getPublicId(),ref.getMediaType(),ref.getFormat())))
                .orElse(null);
    }
    @Transactional
    public View upload(Actor actor, Long saleId, MultipartFile file) {
        authorize(actor,saleId,true);
        // A single attachment per attention. Repeated requests after a timeout do not create duplicates.
        CutReference existing = references.findByTenantIdAndSaleId(actor.tenantId(),saleId).orElse(null);
        if (existing != null) return new View(saleId,existing.getMediaType(),storage.cutReferenceUrl(existing.getPublicId(),existing.getMediaType(),existing.getFormat()));
        var uploaded=storage.uploadCutReference(actor.tenantId(),actor.userId(),file);
        var ref=new CutReference();ref.setTenantId(actor.tenantId());ref.setSaleId(saleId);
        ref.setPublicId(uploaded.publicId());ref.setMediaType(uploaded.mediaType());ref.setFormat(uploaded.format());ref.setCreatedAt(LocalDateTime.now());
        try { references.saveAndFlush(ref); }
        catch (RuntimeException e) { storage.deleteCutReference(uploaded.publicId(),uploaded.mediaType()); throw e; }
        // Do not sign URLs after saving: a delivery error must not roll back the stored attachment.
        return new View(saleId,ref.getMediaType(),null);
    }
}
