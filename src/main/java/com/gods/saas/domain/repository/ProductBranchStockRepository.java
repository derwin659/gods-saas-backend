package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.ProductBranchStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBranchStockRepository extends JpaRepository<ProductBranchStock, Long> {

    Optional<ProductBranchStock> findByTenant_IdAndBranch_IdAndProduct_Id(
            Long tenantId,
            Long branchId,
            Long productId
    );

    @Query("""
        select pbs
        from ProductBranchStock pbs
        join fetch pbs.product p
        where pbs.tenant.id = :tenantId
          and pbs.branch.id = :branchId
          and (:activeOnly is null or p.activo = :activeOnly)
        order by p.nombre asc
    """)
    List<ProductBranchStock> findByTenantAndBranchWithProduct(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("activeOnly") Boolean activeOnly
    );
}
