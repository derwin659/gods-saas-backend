package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.SaleItem;
import com.gods.saas.domain.repository.projection.CustomerHistorySaleItemProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    List<CustomerHistorySaleItemProjection> findCustomerHistoryItemsBySale(Long tenantId, Long customerId, Long id);
}
