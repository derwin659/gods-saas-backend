package com.gods.saas.service.impl;

import com.gods.saas.exception.ShowcaseUploadRateLimitException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShowcaseUploadRateLimitService {
    private static final Duration IMAGE_WINDOW = Duration.ofMinutes(15);
    private static final Duration VIDEO_WINDOW = Duration.ofMinutes(30);
    private static final Duration ACTIVE_UPLOAD_TTL = Duration.ofMinutes(5);
    private static final int USER_IMAGE_LIMIT = 10;
    private static final int USER_VIDEO_LIMIT = 4;
    private static final int TENANT_IMAGE_LIMIT = 50;
    private static final int TENANT_VIDEO_LIMIT = 20;

    private final JdbcTemplate jdbc;

    public ShowcaseUploadRateLimitService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Lease acquire(Long tenantId, Long userId, String rawMediaType) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("No se pudo identificar al usuario de la carga");
        }
        String mediaType = "VIDEO".equalsIgnoreCase(rawMediaType) ? "VIDEO" : "IMAGE";
        Duration window = "VIDEO".equals(mediaType) ? VIDEO_WINDOW : IMAGE_WINDOW;
        UUID token = UUID.randomUUID();
        acquireScope(tenantId, "USER", userId, mediaType,
                "VIDEO".equals(mediaType) ? USER_VIDEO_LIMIT : USER_IMAGE_LIMIT,
                window, token, true);
        acquireScope(tenantId, "TENANT", 0L, mediaType,
                "VIDEO".equals(mediaType) ? TENANT_VIDEO_LIMIT : TENANT_IMAGE_LIMIT,
                window, token, false);
        return new Lease(this, tenantId, userId, mediaType, token);
    }

    private void acquireScope(Long tenantId, String scope, Long subjectId, String mediaType,
                              int limit, Duration window, UUID token, boolean preventConcurrent) {
        String sql = """
                INSERT INTO showcase_upload_rate_limit
                    (tenant_id, scope_type, subject_id, media_type, window_started_at,
                     attempts, active_token, active_since, updated_at)
                VALUES (?, ?, ?, ?, NOW(), 1,
                        CASE WHEN ? THEN ?::uuid ELSE NULL END,
                        CASE WHEN ? THEN NOW() ELSE NULL END, NOW())
                ON CONFLICT (tenant_id, scope_type, subject_id, media_type) DO UPDATE SET
                    window_started_at = CASE
                        WHEN showcase_upload_rate_limit.window_started_at <= NOW() - (? * INTERVAL '1 second')
                        THEN NOW() ELSE showcase_upload_rate_limit.window_started_at END,
                    attempts = CASE
                        WHEN showcase_upload_rate_limit.window_started_at <= NOW() - (? * INTERVAL '1 second')
                        THEN 1 ELSE showcase_upload_rate_limit.attempts + 1 END,
                    active_token = CASE WHEN ? THEN ?::uuid ELSE NULL END,
                    active_since = CASE WHEN ? THEN NOW() ELSE NULL END,
                    updated_at = NOW()
                WHERE
                    (NOT ? OR showcase_upload_rate_limit.active_token IS NULL
                        OR showcase_upload_rate_limit.active_since < NOW() - (? * INTERVAL '1 second'))
                    AND (showcase_upload_rate_limit.window_started_at <= NOW() - (? * INTERVAL '1 second')
                        OR showcase_upload_rate_limit.attempts < ?)
                RETURNING attempts
                """;
        List<Integer> accepted = jdbc.query(sql,
                (rs, rowNum) -> rs.getInt("attempts"),
                tenantId, scope, subjectId, mediaType,
                preventConcurrent, token, preventConcurrent,
                window.toSeconds(), window.toSeconds(),
                preventConcurrent, token, preventConcurrent,
                preventConcurrent, ACTIVE_UPLOAD_TTL.toSeconds(),
                window.toSeconds(), limit);
        if (!accepted.isEmpty()) return;
        rejectWithCurrentState(tenantId, scope, subjectId, mediaType, window, preventConcurrent);
    }

    private void rejectWithCurrentState(Long tenantId, String scope, Long subjectId,
                                        String mediaType, Duration window, boolean preventConcurrent) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT window_started_at, attempts, active_token, active_since
                FROM showcase_upload_rate_limit
                WHERE tenant_id=? AND scope_type=? AND subject_id=? AND media_type=?
                """, tenantId, scope, subjectId, mediaType);
        if (rows.isEmpty()) {
            throw new ShowcaseUploadRateLimitException("No se pudo reservar la carga. Inténtalo nuevamente.", 5);
        }
        Map<String, Object> row = rows.getFirst();
        Timestamp activeSince = (Timestamp) row.get("active_since");
        if (preventConcurrent && row.get("active_token") != null && activeSince != null
                && activeSince.toInstant().isAfter(Instant.now().minus(ACTIVE_UPLOAD_TTL))) {
            throw new ShowcaseUploadRateLimitException(
                    "Ya tienes una carga en proceso. Espera a que termine o cancélala antes de iniciar otra.", 10);
        }
        Timestamp started = (Timestamp) row.get("window_started_at");
        long retry = started == null ? window.toSeconds()
                : Math.max(1, Duration.between(Instant.now(), started.toInstant().plus(window)).toSeconds());
        throw new ShowcaseUploadRateLimitException(
                "Alcanzaste el límite temporal de cargas. Podrás intentarlo nuevamente en " + humanWait(retry) + ".",
                retry);
    }

    private String humanWait(long seconds) {
        if (seconds < 60) return seconds + " segundos";
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long tenantId, Long userId, String mediaType, UUID token) {
        jdbc.update("""
                UPDATE showcase_upload_rate_limit
                SET active_token=NULL, active_since=NULL, updated_at=NOW()
                WHERE tenant_id=? AND scope_type='USER' AND subject_id=?
                  AND media_type=? AND active_token=?::uuid
                """, tenantId, userId, mediaType, token);
    }

    public static final class Lease implements AutoCloseable {
        private final ShowcaseUploadRateLimitService owner;
        private final Long tenantId;
        private final Long userId;
        private final String mediaType;
        private final UUID token;
        private boolean closed;

        private Lease(ShowcaseUploadRateLimitService owner, Long tenantId, Long userId,
                      String mediaType, UUID token) {
            this.owner = owner;
            this.tenantId = tenantId;
            this.userId = userId;
            this.mediaType = mediaType;
            this.token = token;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            owner.release(tenantId, userId, mediaType, token);
        }
    }
}