package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.RewardItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RewardItemRepository extends JpaRepository<RewardItem, Long> {

    List<RewardItem> findByTenantIdAndActivoTrueOrderByPuntosRequeridosAsc(Long tenantId);

    Optional<RewardItem> findByIdAndTenant_IdAndActivoTrue(Long rewardId, Long tenantId);
}
