package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberCommissionItem;
import com.gods.saas.domain.dto.response.BarberCommissionResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.projection.BarberCommissionDailyProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BarberCommissionService {

    private final SaleRepository saleRepository;
    private final AppUserRepository appUserRepository;


    public BarberCommissionResponse getCommissions(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();

        if (from == null && to == null) {
            from = today;
            to = today;
        } else if (from == null) {
            from = to;
        } else if (to == null) {
            to = from;
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser mayor que la fecha final");
        }

        // =========================================================
        // 1) Obtener contexto del usuario/barbero logueado
        // =========================================================
        AppUser barber = getCurrentBarber();
        Long tenantId = getCurrentTenantId();
        Long branchId = getCurrentBranchId();

        // =========================================================
        // 2) Determinar porcentaje de comisión
        // =========================================================
        BigDecimal  porcentajeComision = resolveCommissionPercentage(barber);

        // =========================================================
        // 3) Consultar ventas por día
        // =========================================================
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<BarberCommissionDailyProjection> rows =
                saleRepository.findDailySalesByBarber(
                        tenantId,
                        branchId,
                        barber.getId(),
                        start,
                        end
                );

        // =========================================================
        // 4) Armar detalle y totales
        // =========================================================
        List<BarberCommissionItem> items = rows.stream()
                .map(row -> {
                    BigDecimal ventas = nvl(row.getVentas());
                    BigDecimal comision = calculateCommission(ventas, porcentajeComision);

                    return BarberCommissionItem.builder()
                            .fecha(row.getFecha())
                            .ventas(ventas)
                            .comision(comision)
                            .build();
                })
                .toList();

        BigDecimal totalVentas = items.stream()
                .map(BarberCommissionItem::getVentas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalComision = items.stream()
                .map(BarberCommissionItem::getComision)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BarberCommissionResponse.builder()
                .barberName(buildFullName(barber))
                .from(from)
                .to(to)
                .totalVentas(totalVentas.setScale(2, RoundingMode.HALF_UP))
                .totalComision(totalComision.setScale(2, RoundingMode.HALF_UP))
                .porcentajeComision(porcentajeComision)
                .items(items)
                .build();
    }

    private BigDecimal calculateCommission(BigDecimal ventas, BigDecimal porcentajeComision) {
        if (porcentajeComision == null || porcentajeComision.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return ventas
                .multiply(porcentajeComision)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String buildFullName(AppUser user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        String apellido = user.getApellido() == null ? "" : user.getApellido().trim();
        return (nombre + " " + apellido).trim();
    }

    /**
     * Regla:
     * - Si el barbero es PORCENTAJE, usar su porcentaje configurado.
     * - Si es SUELDO, devolver 0.
     *
     * Ajusta esta lógica a tus nombres reales de campos.
     */
    private BigDecimal resolveCommissionPercentage(AppUser barber) {
        if (barber == null) {
            return BigDecimal.ZERO;
        }

        if (Boolean.TRUE.equals(barber.getSalaryMode())) {
            return BigDecimal.ZERO;
        }

        return barber.getCommissionPercentage() == null
                ? BigDecimal.ZERO
                : barber.getCommissionPercentage();
    }
    // =========================================================
    // Métodos de contexto / seguridad
    // Reemplázalos por tu implementación real
    // =========================================================



    private AppUser getCurrentBarber() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("Usuario no autenticado");
        }

        Long userId;

        try {
            userId = Long.valueOf(auth.getPrincipal().toString());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo obtener el userId del token");
        }

        return appUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));
    }

    private Long getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getDetails() == null) {
            throw new RuntimeException("No se pudo obtener el tenant del usuario autenticado");
        }

        Object details = auth.getDetails();

        if (!(details instanceof Map<?, ?> map)) {
            throw new RuntimeException("Los detalles de autenticación no contienen el tenantId");
        }

        Object tenantIdValue = map.get("tenantId");

        if (tenantIdValue == null) {
            throw new RuntimeException("tenantId no encontrado en el token");
        }

        try {
            return Long.valueOf(tenantIdValue.toString());
        } catch (Exception e) {
            throw new RuntimeException("tenantId inválido en el token");
        }
    }

    private Long getCurrentBranchId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getDetails() == null) {
            throw new RuntimeException("No se pudo obtener la sucursal del usuario autenticado");
        }

        Object details = auth.getDetails();

        if (!(details instanceof Map<?, ?> map)) {
            throw new RuntimeException("Los detalles de autenticación no contienen el branchId");
        }

        Object branchIdValue = map.get("branchId");

        if (branchIdValue == null) {
            throw new RuntimeException("branchId no encontrado en el token");
        }

        try {
            return Long.valueOf(branchIdValue.toString());
        } catch (Exception e) {
            throw new RuntimeException("branchId inválido en el token");
        }
    }
}