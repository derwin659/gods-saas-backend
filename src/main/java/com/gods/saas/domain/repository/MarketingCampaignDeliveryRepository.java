package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.MarketingCampaignDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MarketingCampaignDeliveryRepository extends JpaRepository<MarketingCampaignDelivery, Long> {

    @Query("""
        select count(d) > 0
        from MarketingCampaignDelivery d
        where d.tenant.id = :tenantId
          and d.customer.id = :customerId
          and d.campaignCode = :campaignCode
          and d.sentAt >= :since
    """)
    boolean existsRecently(Long tenantId, Long customerId, String campaignCode, LocalDateTime since);

    long countByTenant_Id(Long tenantId);

    List<MarketingCampaignDelivery> findTop100ByTenant_IdOrderBySentAtDesc(Long tenantId);
}