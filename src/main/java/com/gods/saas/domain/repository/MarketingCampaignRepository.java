package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.MarketingCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketingCampaignRepository extends JpaRepository<MarketingCampaign, Long> {

    List<MarketingCampaign> findByTenant_IdAndEnabledTrue(Long tenantId);

    Optional<MarketingCampaign> findByTenant_IdAndCode(Long tenantId, String code);
}