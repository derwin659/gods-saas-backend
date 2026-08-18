package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.ElectronicDocument;
import com.gods.saas.invoicing.ElectronicDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ElectronicDocumentRepository extends JpaRepository<ElectronicDocument, Long> {
    Optional<ElectronicDocument> findByTenantIdAndSaleIdAndDocumentType(
            Long tenantId, Long saleId, ElectronicDocumentType documentType);
    List<ElectronicDocument> findByTenantIdAndSaleIdOrderByCreatedAtDesc(Long tenantId, Long saleId);
}
