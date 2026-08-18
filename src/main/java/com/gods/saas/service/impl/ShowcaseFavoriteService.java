package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowcaseFavoriteService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Long> list(Long tenantId, Long clientUserId) {
        return jdbc.queryForList("""
                SELECT f.showcase_id
                FROM showcase_favorite f
                JOIN professional_showcase p ON p.showcase_id=f.showcase_id
                WHERE f.tenant_id=? AND f.client_user_id=?
                  AND p.tenant_id=? AND p.status='PUBLISHED'
                  AND p.published_at<=CURRENT_TIMESTAMP
                ORDER BY f.created_at DESC
                """, Long.class, tenantId, clientUserId, tenantId);
    }

    @Transactional
    public void add(Long tenantId, Long clientUserId, Long showcaseId) {
        Integer valid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM professional_showcase
                WHERE showcase_id=? AND tenant_id=? AND status='PUBLISHED'
                  AND published_at<=CURRENT_TIMESTAMP
                """, Integer.class, showcaseId, tenantId);
        if (valid == null || valid == 0) {
            throw new IllegalArgumentException("Publicacion no disponible.");
        }
        jdbc.update("""
                INSERT INTO showcase_favorite
                    (tenant_id, showcase_id, client_user_id, created_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (showcase_id, client_user_id) DO NOTHING
                """, tenantId, showcaseId, clientUserId);
    }

    @Transactional
    public void remove(Long tenantId, Long clientUserId, Long showcaseId) {
        jdbc.update("""
                DELETE FROM showcase_favorite
                WHERE tenant_id=? AND client_user_id=? AND showcase_id=?
                """, tenantId, clientUserId, showcaseId);
    }
}