package com.gods.saas.domain.repository;
import com.gods.saas.domain.model.CutReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CutReferenceRepository extends JpaRepository<CutReference,Long> {
    Optional<CutReference> findByTenantIdAndSaleId(Long tenantId,Long saleId);
}
