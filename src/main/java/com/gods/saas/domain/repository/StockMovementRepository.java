package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.StockMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findBySale_Id(Long saleId);

    List<StockMovement> findByTenant_IdAndBranch_IdAndProduct_IdOrderByFechaCreacionDesc(
            Long tenantId,
            Long branchId,
            Long productId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StockMovement sm where sm.sale.id = :saleId")
    void deleteBySaleId(@Param("saleId") Long saleId);
}
