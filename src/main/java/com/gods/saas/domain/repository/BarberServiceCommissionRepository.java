package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.BarberServiceCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface BarberServiceCommissionRepository extends JpaRepository<BarberServiceCommission, Long> {
    List<BarberServiceCommission> findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(Long tenantId, Long branchId, Long barberId);
    Optional<BarberServiceCommission> findByTenant_IdAndBranch_IdAndBarber_IdAndService_Id(Long tenantId, Long branchId, Long barberId, Long serviceId);
}
