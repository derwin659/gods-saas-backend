package com.gods.saas.domain.repository;
import com.gods.saas.domain.enums.ShowcaseStatus;
import com.gods.saas.domain.model.ProfessionalShowcase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProfessionalShowcaseRepository extends JpaRepository<ProfessionalShowcase,Long>{
 List<ProfessionalShowcase> findByTenant_IdAndProfessional_IdOrderByCreatedAtDesc(Long tenantId,Long professionalId);
 List<ProfessionalShowcase> findByTenant_IdAndStatusOrderByCreatedAtDesc(Long tenantId,ShowcaseStatus status);
 List<ProfessionalShowcase> findByTenant_IdOrderByCreatedAtDesc(Long tenantId);
 List<ProfessionalShowcase> findByTenant_IdAndBranch_IdAndStatusOrderByPublishedAtDesc(Long tenantId,Long branchId,ShowcaseStatus status);
 List<ProfessionalShowcase> findByTenant_IdAndBranch_IdAndProfessional_IdAndStatusOrderByPublishedAtDesc(Long tenantId,Long branchId,Long professionalId,ShowcaseStatus status);
 long countByTenant_IdAndProfessional_IdAndStatusNot(Long tenantId,Long professionalId,ShowcaseStatus status);
 long countByTenant_IdAndProfessional_IdAndMediaTypeAndStatusNot(Long tenantId,Long professionalId,String mediaType,ShowcaseStatus status);
 Optional<ProfessionalShowcase> findByIdAndTenant_Id(Long id,Long tenantId);
}