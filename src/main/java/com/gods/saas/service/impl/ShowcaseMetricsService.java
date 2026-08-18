package com.gods.saas.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ShowcaseMetricsService {
    private static final Set<String> ALLOWED_EVENTS = Set.of(
            "VIEW", "VIDEO_START", "VIDEO_COMPLETE", "RESERVE_CLICK", "BOOKING_CONFIRMED", "SHARE"
    );
    private final JdbcTemplate jdbc;

    public ShowcaseMetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(Long showcaseId, String rawEventType, String rawViewerKey) {
        if (showcaseId == null) throw new IllegalArgumentException("Publicación no encontrada.");
        String eventType = rawEventType == null ? "" : rawEventType.trim().toUpperCase();
        if (!ALLOWED_EVENTS.contains(eventType)) {
            throw new IllegalArgumentException("Tipo de evento de Vitrina inválido.");
        }
        String viewerKey = rawViewerKey == null ? "" : rawViewerKey.trim();
        if (!viewerKey.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("Identificador de sesión inválido.");
        }
        List<Long> tenants = jdbc.query("""
                SELECT tenant_id FROM professional_showcase
                WHERE showcase_id=? AND status='PUBLISHED'
                """, (rs, rowNum) -> rs.getLong(1), showcaseId);
        if (tenants.isEmpty()) return;
        jdbc.update("""
                INSERT INTO showcase_event
                    (tenant_id, showcase_id, event_type, viewer_key, event_date, created_at)
                VALUES (?, ?, ?, ?, CURRENT_DATE, NOW())
                ON CONFLICT (showcase_id, event_type, viewer_key, event_date) DO NOTHING
                """, tenants.getFirst(), showcaseId, eventType, viewerKey);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ownerMetrics(Long tenantId) {
        return jdbc.queryForList("""
                SELECT p.showcase_id AS "showcaseId",
                       COUNT(e.showcase_event_id) FILTER (WHERE e.event_type='VIEW') AS views,
                       COUNT(e.showcase_event_id) FILTER (WHERE e.event_type='VIDEO_START') AS "videoStarts",
                       COUNT(e.showcase_event_id) FILTER (WHERE e.event_type='VIDEO_COMPLETE') AS "videoCompletes",
                       COUNT(e.showcase_event_id) FILTER (WHERE e.event_type='RESERVE_CLICK') AS "reserveClicks",
                       COUNT(e.showcase_event_id) FILTER (WHERE e.event_type='BOOKING_CONFIRMED') AS bookings,
                       COUNT(e.showcase_event_id) FILTER (WHERE e.event_type='SHARE') AS shares,
                       (SELECT COUNT(*) FROM showcase_favorite f WHERE f.showcase_id=p.showcase_id) AS favorites
                FROM professional_showcase p
                LEFT JOIN showcase_event e ON e.showcase_id=p.showcase_id
                WHERE p.tenant_id=?
                GROUP BY p.showcase_id
                """, tenantId);
    }
}