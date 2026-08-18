package com.gods.saas.domain.repository;
import com.gods.saas.domain.enums.ShowcaseStatus;
import com.gods.saas.domain.model.ProfessionalShowcase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ProfessionalShowcaseRepository extends JpaRepository<ProfessionalShowcase,Long>{
 List<ProfessionalShowcase> findByTenant_IdAndProfessional_IdOrderByCreatedAtDesc(Long tenantId,Long professionalId);
 List<ProfessionalShowcase> findByTenant_IdAndStatusOrderByCreatedAtDesc(Long tenantId,ShowcaseStatus status);
 List<ProfessionalShowcase> findByTenant_IdAndStatusOrderByFeaturedDescSortOrderAscCreatedAtDesc(Long tenantId,ShowcaseStatus status);
 List<ProfessionalShowcase> findByTenant_IdOrderByCreatedAtDesc(Long tenantId);
 long countByTenant_IdAndStatusAndFeaturedTrue(Long tenantId,ShowcaseStatus status);
 List<ProfessionalShowcase> findByTenant_IdAndStatusAndFeaturedTrueOrderBySortOrderAscPublishedAtDesc(Long tenantId,ShowcaseStatus status);
 long countByTenant_IdAndProfessional_IdAndStatusNot(Long tenantId,Long professionalId,ShowcaseStatus status);
 long countByTenant_IdAndProfessional_IdAndMediaTypeAndStatusNot(Long tenantId,Long professionalId,String mediaType,ShowcaseStatus status);
 Optional<ProfessionalShowcase> findByIdAndTenant_Id(Long id,Long tenantId);
 @Query("""
  select distinct p from ProfessionalShowcase p
  left join p.selectedBranches sb
  where p.tenant.id=:tenantId and p.status=:status
    and p.publishedAt<=CURRENT_TIMESTAMP
    and ((p.originType='PROFESSIONAL_WORK' and p.branch.id=:branchId)
      or (p.originType='TENANT_CATALOG' and (p.visibilityScope='ALL_BRANCHES' or p.branch.id=:branchId or sb.id=:branchId)))
    and (:professionalId is null or p.professional.id=:professionalId)
  order by p.featured desc,p.sortOrder asc,p.publishedAt desc
 """)
 List<ProfessionalShowcase> findPublishedForBranch(@Param("tenantId")Long tenantId,@Param("branchId")Long branchId,@Param("professionalId")Long professionalId,@Param("status")ShowcaseStatus status);
 @Query(value="""
  select distinct p from ProfessionalShowcase p
  left join p.selectedBranches sb
  where p.tenant.id=:tenantId and p.status=:status
    and p.publishedAt<=CURRENT_TIMESTAMP
    and ((p.originType='PROFESSIONAL_WORK' and p.branch.id in :branchIds)
      or (p.originType='TENANT_CATALOG' and (p.visibilityScope='ALL_BRANCHES' or p.branch.id in :branchIds or sb.id in :branchIds)))
    and (:professionalId is null or p.professional.id=:professionalId)
  order by p.featured desc,p.sortOrder asc,p.publishedAt desc,p.id desc
 """, countQuery="""
  select count(distinct p.id) from ProfessionalShowcase p
  left join p.selectedBranches sb
  where p.tenant.id=:tenantId and p.status=:status
    and p.publishedAt<=CURRENT_TIMESTAMP
    and ((p.originType='PROFESSIONAL_WORK' and p.branch.id in :branchIds)
      or (p.originType='TENANT_CATALOG' and (p.visibilityScope='ALL_BRANCHES' or p.branch.id in :branchIds or sb.id in :branchIds)))
    and (:professionalId is null or p.professional.id=:professionalId)
 """)
 Page<ProfessionalShowcase> findPublishedPage(
   @Param("tenantId")Long tenantId,
   @Param("branchIds")Collection<Long> branchIds,
   @Param("professionalId")Long professionalId,
   @Param("status")ShowcaseStatus status,
   Pageable pageable);
}