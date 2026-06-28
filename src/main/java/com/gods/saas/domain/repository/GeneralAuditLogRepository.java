package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.GeneralAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface GeneralAuditLogRepository extends JpaRepository<GeneralAuditLog, Long> {
    @Query("""
        select log from GeneralAuditLog log
        where log.tenantId = :tenantId
          and (:branchId is null or log.branchId = :branchId)
          and (:actorUserId is null or log.actorUserId = :actorUserId)
          and (:entityType is null or log.entityType = :entityType)
          and (:action is null or log.action = :action)
          and log.createdAt between :from and :to
        order by log.createdAt desc
        """)
    List<GeneralAuditLog> search(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("actorUserId") Long actorUserId,
        @Param("entityType") String entityType,
        @Param("action") String action,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
}
