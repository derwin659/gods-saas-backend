package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reserve attempts independently: provider failures must not reset the spending allowance. */
@Service
@RequiredArgsConstructor
public class WhatsappAssistantUsageService {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(Long tenantId, String month, int limit) {
        if (limit <= 0) return false;
        jdbc.update("insert into whatsapp_assistant_usage(tenant_id,month_key) values (?,?) on conflict do nothing", tenantId, month);
        return jdbc.update("update whatsapp_assistant_usage set turns=turns+1 where tenant_id=? and month_key=? and turns<?", tenantId, month, limit) == 1;
    }

    public int used(Long tenantId, String month) {
        var rows = jdbc.queryForList("select turns from whatsapp_assistant_usage where tenant_id=? and month_key=?", Integer.class, tenantId, month);
        return rows.isEmpty() ? 0 : rows.get(0);
    }
}
