package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.ShowcaseStatus;
import com.gods.saas.domain.model.ProfessionalShowcase;
import com.gods.saas.domain.repository.ProfessionalShowcaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShowcaseReportService {
    private static final Set<String> REASONS = Set.of(
            "INAPPROPRIATE", "PERSONAL_DATA", "NO_CONSENT", "MISLEADING", "SPAM", "OTHER"
    );
    private final JdbcTemplate jdbc;
    private final ProfessionalShowcaseRepository repository;

    @Transactional
    public void report(Long showcaseId, String rawReason, String rawDetails, String rawReporterKey) {
        String reason = rawReason == null ? "" : rawReason.trim().toUpperCase();
        if (!REASONS.contains(reason)) throw new IllegalArgumentException("Motivo de reporte invalido.");
        String reporterKey = rawReporterKey == null ? "" : rawReporterKey.trim();
        if (!reporterKey.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("Identificador de reporte invalido.");
        }
        String details = rawDetails == null ? null : rawDetails.trim();
        if (details != null && details.isEmpty()) details = null;
        if (details != null && details.length() > 300) {
            throw new IllegalArgumentException("El detalle puede tener hasta 300 caracteres.");
        }
        List<Long> tenants = jdbc.query("""
                SELECT tenant_id FROM professional_showcase
                WHERE showcase_id=? AND status='PUBLISHED'
                  AND published_at<=CURRENT_TIMESTAMP
                """, (rs, rowNum) -> rs.getLong(1), showcaseId);
        if (tenants.isEmpty()) throw new IllegalArgumentException("Publicacion no disponible.");
        jdbc.update("""
                INSERT INTO showcase_report
                    (tenant_id, showcase_id, reason, details, reporter_key, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'OPEN', NOW())
                ON CONFLICT (showcase_id, reporter_key)
                DO UPDATE SET reason=EXCLUDED.reason, details=EXCLUDED.details,
                              status='OPEN', created_at=NOW(),
                              reviewed_by_user_id=NULL, reviewed_at=NULL,
                              resolution_note=NULL
                """, tenants.getFirst(), showcaseId, reason, details, reporterKey);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ownerReports(Long tenantId, Long showcaseId) {
        return jdbc.queryForList("""
                SELECT report_id AS "reportId", showcase_id AS "showcaseId",
                       reason, details, status, created_at AS "createdAt",
                       reviewed_at AS "reviewedAt", resolution_note AS "resolutionNote"
                FROM showcase_report
                WHERE tenant_id=? AND (? IS NULL OR showcase_id=?)
                ORDER BY CASE WHEN status='OPEN' THEN 0 ELSE 1 END, created_at DESC
                LIMIT 250
                """, tenantId, showcaseId, showcaseId);
    }

    @Transactional
    public void resolve(Long tenantId, Long actorId, Long reportId, String rawDecision, String rawNote) {
        String decision = rawDecision == null ? "" : rawDecision.trim().toUpperCase();
        if (!Set.of("DISMISSED", "REVIEWED", "CONTENT_HIDDEN").contains(decision)) {
            throw new IllegalArgumentException("Decision de moderacion invalida.");
        }
        String note = rawNote == null ? null : rawNote.trim();
        if (note != null && note.length() > 300) throw new IllegalArgumentException("La nota puede tener hasta 300 caracteres.");
        List<Long> showcaseIds = jdbc.query("""
                SELECT showcase_id FROM showcase_report WHERE report_id=? AND tenant_id=?
                """, (rs, rowNum) -> rs.getLong(1), reportId, tenantId);
        if (showcaseIds.isEmpty()) throw new IllegalArgumentException("Reporte no encontrado.");
        if ("CONTENT_HIDDEN".equals(decision)) {
            ProfessionalShowcase item = repository.findByIdAndTenant_Id(showcaseIds.getFirst(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Publicacion no encontrada."));
            item.setStatus(ShowcaseStatus.ARCHIVED);
            item.setFeatured(false);
            item.setSortOrder(0);
            item.setPublishedAt(null);
            item.setArchivedAt(LocalDateTime.now());
            repository.save(item);
        }
        jdbc.update("""
                UPDATE showcase_report
                SET status=?, reviewed_by_user_id=?, reviewed_at=NOW(), resolution_note=?
                WHERE report_id=? AND tenant_id=?
                """, decision, actorId, note, reportId, tenantId);
    }
}