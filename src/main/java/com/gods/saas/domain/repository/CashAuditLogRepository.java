package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CashAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CashAuditLogRepository extends JpaRepository<CashAuditLog, Long> {

    @Query("""
        select log
        from CashAuditLog log
        left join fetch log.actorUser
        left join fetch log.cashRegister
        where log.tenant.id = :tenantId
          and log.branch.id = :branchId
          and (:cashRegisterId is null or log.cashRegister.id = :cashRegisterId)
          and log.createdAt >= :from
          and log.createdAt < :to
        order by log.createdAt desc, log.id desc
        """)
    List<CashAuditLog> findByBranchAndRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("cashRegisterId") Long cashRegisterId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}