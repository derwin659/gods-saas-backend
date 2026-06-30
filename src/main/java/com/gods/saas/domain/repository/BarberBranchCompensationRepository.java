package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.BarberBranchCompensation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BarberBranchCompensationRepository extends JpaRepository<BarberBranchCompensation, Long> {
    Optional<BarberBranchCompensation> findByTenant_IdAndBranch_IdAndBarber_Id(
            Long tenantId, Long branchId, Long barberUserId
    );

    List<BarberBranchCompensation> findByTenant_IdAndBarber_IdOrderByBranch_NombreAsc(
            Long tenantId, Long barberUserId
    );
}
