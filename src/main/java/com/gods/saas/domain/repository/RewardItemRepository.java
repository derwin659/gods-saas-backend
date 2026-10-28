package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.RewardItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RewardItemRepository extends JpaRepository<RewardItem, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from RewardItem r where r.id=:rewardId and r.tenant.id=:tenantId and r.activo=true")
    Optional<RewardItem> findForRedemption(@org.springframework.data.repository.query.Param("rewardId") Long rewardId,
            @org.springframework.data.repository.query.Param("tenantId") Long tenantId);

    long countByTenant_Id(Long tenantId);

    List<RewardItem> findByTenant_IdAndActivoTrueOrderByPuntosRequeridosAsc(Long tenantId);

    Optional<RewardItem> findByIdAndTenant_IdAndActivoTrue(Long rewardId, Long tenantId);

    List<RewardItem> findByTenant_IdOrderByNombreAsc(Long tenantId);

    List<RewardItem> findByTenant_IdAndActivoTrueOrderByNombreAsc(Long tenantId);

    Optional<RewardItem> findByIdAndTenant_Id(Long id, Long tenantId);

    boolean existsByTenant_IdAndNombreIgnoreCase(Long tenantId, String nombre);

    boolean existsByTenant_IdAndNombreIgnoreCaseAndIdNot(Long tenantId, String nombre, Long id);
}