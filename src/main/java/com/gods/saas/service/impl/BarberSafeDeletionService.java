package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.DeleteBarberRequest;
import com.gods.saas.domain.dto.response.BarberDeletionPreviewResponse;
import com.gods.saas.domain.dto.response.BarberDeletionResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class BarberSafeDeletionService {
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbc;
    private final GeneralAuditService auditService;

    @Transactional(readOnly = true)
    public BarberDeletionPreviewResponse preview(Long tenantId, Long barberUserId) {
        AppUser barber = getBarber(tenantId, barberUserId);
        long future = count("select count(*) from appointment where tenant_id=? and user_id=? and fecha >= current_date and upper(coalesce(estado,'')) not in ('CANCELLED','CANCELED','CANCELADO','COMPLETED','COMPLETADO')", tenantId, barberUserId);
        long historical = count("select count(*) from appointment where tenant_id=? and user_id=?", tenantId, barberUserId) - future;
        long sales = count("select count(*) from sale_item where barber_user_id=?", barberUserId);
        long payments = count("select count(*) from barber_payment where tenant_id=? and barber_user_id=?", tenantId, barberUserId);
        long advances = count("select count(*) from cash_movement where tenant_id=? and barber_user_id=?", tenantId, barberUserId);
        long schedules = count("select count(*) from barber_availability where barber_user_id=?", barberUserId)
                + count("select count(*) from barber_time_block where barber_user_id=?", barberUserId);
        long configurations = count("select count(*) from barber_branch_service where tenant_id=? and barber_user_id=?", tenantId, barberUserId)
                + count("select count(*) from barber_service_commission where tenant_id=? and barber_user_id=?", tenantId, barberUserId)
                + count("select count(*) from barber_branch_compensation where tenant_id=? and barber_user_id=?", tenantId, barberUserId);
        long otherHistory = count("select count(*) from sale where tenant_id=? and tip_barber_user_id=?", tenantId, barberUserId)
                + count("select count(*) from customer_cut_history where barber_user_id=?", barberUserId)
                + count("select count(*) from local_consumption_order_item where barber_user_id=?", barberUserId)
                + count("select count(*) from general_audit_log where tenant_id=? and entity_type='BARBER' and entity_id=?", tenantId, barberUserId);
        boolean history = historical + sales + payments + advances + otherHistory > 0;
        boolean blocked = future > 0;
        String mode = blocked ? "BLOCKED_FUTURE_APPOINTMENTS" : (history ? "RETIRE" : "HARD_DELETE");
        String explanation = blocked
                ? "Primero cancela o reasigna las citas futuras del profesional."
                : history ? "Se retirará del negocio y se conservarán ventas, citas y pagos históricos."
                : "Se eliminará definitivamente porque no tiene historial operativo.";
        return BarberDeletionPreviewResponse.builder()
                .barberUserId(barberUserId).barberName(fullName(barber))
                .futureAppointments(future).historicalAppointments(Math.max(0, historical))
                .saleItems(sales).payments(payments).advances(advances)
                .schedules(schedules).configurations(configurations)
                .hasHistory(history).blocked(blocked).deletionMode(mode).explanation(explanation).build();
    }

    @Transactional
    public BarberDeletionResponse delete(Long tenantId, Long actorUserId, String actorRole,
                                         Long barberUserId, DeleteBarberRequest request) {
        AppUser barber = getBarber(tenantId, barberUserId);
        if (barberUserId.equals(actorUserId)) throw new BusinessException("No puedes retirar tu propia cuenta desde esta opción.");
        BarberDeletionPreviewResponse preview = preview(tenantId, barberUserId);
        if (preview.isBlocked()) throw new BusinessException(preview.getExplanation());
        Map<String,Object> before = Map.of("name", fullName(barber), "active", Boolean.TRUE.equals(barber.getActivo()),
                "futureAppointments", preview.getFutureAppointments(), "historicalAppointments", preview.getHistoricalAppointments(),
                "saleItems", preview.getSaleItems(), "payments", preview.getPayments(), "mode", preview.getDeletionMode());

        deleteFutureConfiguration(tenantId, barberUserId);
        if (preview.isHasHistory()) {
            jdbc.update("delete from user_tenant_roles where tenant_id=? and user_id=? and role='BARBER'", tenantId, barberUserId);
            if ("BARBER".equalsIgnoreCase(barber.getRol())) barber.setActivo(false);
            barber.setRetiredAt(java.time.LocalDateTime.now());
            barber.setRetiredByUserId(actorUserId);
            barber.setRetirementReason(request.reason().trim());
            appUserRepository.save(barber);
        } else {
            jdbc.update("delete from user_tenant_roles where tenant_id=? and user_id=? and role='BARBER'", tenantId, barberUserId);
            appUserRepository.delete(barber);
            appUserRepository.flush();
        }
        auditService.record(tenantId, null, actorUserId, actorRole, "BARBER", barberUserId,
                preview.isHasHistory() ? "RETIRE" : "DELETE", request.reason(), before,
                Map.of("deleted", true, "historyPreserved", preview.isHasHistory()));
        return BarberDeletionResponse.builder().barberUserId(barberUserId).barberName(fullName(barber))
                .deletionMode(preview.getDeletionMode()).deleted(true).historyPreserved(preview.isHasHistory())
                .message(preview.isHasHistory() ? "Profesional retirado. Su historial quedó protegido." : "Profesional eliminado definitivamente.").build();
    }

    private void deleteFutureConfiguration(Long tenantId, Long id) {
        jdbc.update("delete from barber_service_commission where tenant_id=? and barber_user_id=?", tenantId, id);
        jdbc.update("delete from barber_branch_service where tenant_id=? and barber_user_id=?", tenantId, id);
        jdbc.update("delete from barber_branch_compensation where tenant_id=? and barber_user_id=?", tenantId, id);
        jdbc.update("delete from barber_time_block where barber_user_id=?", id);
        jdbc.update("delete from barber_availability where barber_user_id=?", id);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private AppUser getBarber(Long tenantId, Long id) {
        AppUser user = appUserRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new BusinessException("El profesional no existe."));
        boolean barberRole = "BARBER".equalsIgnoreCase(user.getRol()) || count(
                "select count(*) from user_tenant_roles where tenant_id=? and user_id=? and role='BARBER'", tenantId, id) > 0;
        if (!barberRole) throw new BusinessException("El usuario no tiene perfil profesional.");
        return user;
    }

    private String fullName(AppUser user) {
        return ((user.getNombre() == null ? "" : user.getNombre()) + " " + (user.getApellido() == null ? "" : user.getApellido())).trim();
    }
}
