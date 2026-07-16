package com.gods.saas.domain.repository;
import com.gods.saas.domain.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByTenant_IdAndActivoTrueOrderByNombreAsc(Long tenantId);
    List<Branch> findByTenant_IdAndActivoTrue(Long tenantId);
    long countByTenantId(Long tenantId);
    List<Branch> findByTenant_IdOrderByNombreAsc(Long tenantId);
    long countByTenant_Id(Long tenantId);
    Optional<Branch> findByIdAndTenant_Id(Long branchId, Long tenantId);
    Optional<Branch> findByIdAndActivoTrueAndPublicVisibleTrueAndDirectoryEnabledTrue(Long branchId);

    boolean existsByTenant_IdAndActivoTrueAndPublicVisibleTrueAndDirectoryEnabledTrue(Long tenantId);

    boolean existsByTenant_IdAndNombreIgnoreCase(Long tenantId, String nombre);

    boolean existsByTenant_IdAndNombreIgnoreCaseAndIdNot(Long tenantId, String nombre, Long branchId);

    List<Branch> findByTenantIdAndActivoTrueOrderByNombreAsc(Long tenantId);
    @Query("""
            select b from Branch b
            join fetch b.tenant t
            where b.activo = true
              and t.active = true
              and b.publicVisible = true
              and b.directoryEnabled = true
            order by t.nombre asc, b.nombre asc
            """)
    List<Branch> findPublicDirectoryBranches();

    @Query("""
            select b from Branch b
            join fetch b.tenant t
            where b.activo = true
              and t.active = true
              and b.publicVisible = true
              and b.directoryEnabled = true
              and lower(coalesce(b.ciudad, t.ciudad, '')) like lower(concat('%', :city, '%'))
            order by t.nombre asc, b.nombre asc
            """)
    List<Branch> findPublicDirectoryBranchesByCity(@Param("city") String city);
}