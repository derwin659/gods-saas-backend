package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CustomerCutHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerCutHistoryRepository extends JpaRepository<CustomerCutHistory, Long> {

    List<CustomerCutHistory> findByTenant_IdAndCustomer_IdOrderByFechaCorteDescIdDesc(
            Long tenantId,
            Long customerId,
            Pageable pageable
    );

    Optional<CustomerCutHistory> findTopByTenant_IdAndCustomer_IdOrderByFechaCorteDescIdDesc(
            Long tenantId,
            Long customerId
    );

    Optional<CustomerCutHistory> findTopByTenant_IdAndSale_Id(Long tenantId, Long saleId);

    @Modifying
    @Query("""
        delete from CustomerCutHistory h
        where h.tenant.id = :tenantId
          and h.sale.id = :saleId
    """)
    int deleteBySale(@Param("tenantId") Long tenantId,
                     @Param("saleId") Long saleId);
}
