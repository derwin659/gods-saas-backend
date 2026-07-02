package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.BarberBranchService;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BarberBranchServiceRepository extends JpaRepository<BarberBranchService, Long> {
    List<BarberBranchService> findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(Long tenantId, Long branchId, Long barberId);
    boolean existsByTenant_IdAndBranch_IdAndBarber_Id(Long tenantId, Long branchId, Long barberId);
    void deleteByTenant_IdAndBranch_IdAndBarber_Id(Long tenantId, Long branchId, Long barberId);
    void deleteByTenant_IdAndService_Id(Long tenantId, Long serviceId);
    long countByTenant_IdAndService_Id(Long tenantId, Long serviceId);
}
