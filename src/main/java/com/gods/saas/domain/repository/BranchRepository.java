package com.gods.saas.domain.repository;
import com.gods.saas.domain.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {


    List<Branch> findByTenant_IdAndActivoTrueOrderByNombreAsc(Long tenantId);
    List<Branch> findByTenant_IdAndActivoTrue(Long tenantId);

    List<Branch> findByTenant_IdOrderByNombreAsc(Long tenantId);

    Optional<Branch> findByIdAndTenant_Id(Long branchId, Long tenantId);

    boolean existsByTenant_IdAndNombreIgnoreCase(Long tenantId, String nombre);

    boolean existsByTenant_IdAndNombreIgnoreCaseAndIdNot(Long tenantId, String nombre, Long branchId);
}