package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CashAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashAuditLogRepository extends JpaRepository<CashAuditLog, Long> {
}