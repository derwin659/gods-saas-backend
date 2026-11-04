package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Appointment;
import java.util.Locale;
import java.util.Set;

final class BookingReminderPolicy {
    private BookingReminderPolicy() {}
    static boolean isCandidate(Appointment a) {
        if (a == null || a.getTenant() == null || a.getCustomer() == null || a.getFecha() == null || a.getHoraInicio() == null) return false;
        String state = a.getEstado() == null ? "" : a.getEstado().trim().toUpperCase(Locale.ROOT);
        return !state.isEmpty() && !Set.of("CANCELADO", "CANCELADA", "CANCELLED", "CANCELED", "ATENDIDO", "ATENDIDA",
            "COMPLETADO", "COMPLETADA", "COMPLETED", "FINALIZADO", "FINALIZADA", "NO_SHOW", "NO_ASISTIO").contains(state)
            && (!Boolean.TRUE.equals(a.getDepositRequired()) || "PAID".equals(a.getDepositStatus()));
    }
}
