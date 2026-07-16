package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.ClientBusinessSummaryResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.projection.NextAppointmentProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientBusinessDiscoveryService {

    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public List<ClientBusinessSummaryResponse> getMyBusinesses(Authentication authentication) {
        Long authenticatedCustomerId = Long.parseLong(authentication.getName());
        Customer currentCustomer = customerRepository.findById(authenticatedCustomerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        String phone = normalizePhone(currentCustomer.getTelefono());
        if (phone == null) {
            return List.of(toResponse(currentCustomer));
        }

        return customerRepository.findActiveByPhoneAcrossTenants(phone)
                .stream()
                .map(this::toResponse)
                .sorted(Comparator
                        .comparing((ClientBusinessSummaryResponse item) -> item.nextAppointmentDate() == null ? 1 : 0)
                        .thenComparing(item -> item.nextAppointmentDate() == null ? "9999-12-31" : item.nextAppointmentDate())
                        .thenComparing(item -> item.tenantName() == null ? "" : item.tenantName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ClientBusinessSummaryResponse toResponse(Customer customer) {
        Tenant tenant = customer.getTenant();
        Long tenantId = tenant == null ? null : tenant.getId();
        Long customerId = customer.getId();

        LoyaltyAccount loyalty = tenantId == null
                ? null
                : loyaltyAccountRepository.findByTenant_IdAndCustomer_Id(tenantId, customerId).orElse(null);

        int availablePoints = loyalty != null && loyalty.getPuntosDisponibles() != null
                ? loyalty.getPuntosDisponibles()
                : safeInt(customer.getPuntosDisponibles());
        int accumulatedPoints = loyalty != null && loyalty.getPuntosAcumulados() != null
                ? loyalty.getPuntosAcumulados()
                : availablePoints;

        NextAppointmentProjection next = tenantId == null
                ? null
                : appointmentRepository.findNextAppointment(tenantId, customerId, LocalDate.now(), LocalTime.now()).orElse(null);

        LocalDate lastCompletedVisit = tenantId == null
                ? null
                : appointmentRepository.findLastCompletedCustomerVisit(tenantId, customerId);
        long completedVisits = tenantId == null
                ? 0
                : appointmentRepository.countCompletedCustomerVisits(tenantId, customerId);

        String relationLabel = next != null
                ? "Proxima cita"
                : completedVisits > 0
                    ? completedVisits + " visita" + (completedVisits == 1 ? "" : "s")
                    : availablePoints > 0
                        ? availablePoints + " pts"
                        : "Negocio conectado";

        return new ClientBusinessSummaryResponse(
                tenantId,
                tenant == null ? null : tenant.getNombre(),
                tenant == null ? null : tenant.getCodigo(),
                tenant == null ? null : tenant.getLogoUrl(),
                tenant == null ? null : tenant.getBusinessType(),
                tenant == null ? null : tenant.getCiudad(),
                customerId,
                availablePoints,
                accumulatedPoints,
                next == null ? null : next.getAppointmentId(),
                next == null || next.getFecha() == null ? null : next.getFecha().toString(),
                next == null || next.getHoraInicio() == null ? null : next.getHoraInicio().toString(),
                next == null ? null : next.getServicio(),
                next == null ? null : next.getBranch(),
                lastCompletedVisit == null ? null : lastCompletedVisit.toString(),
                completedVisits,
                relationLabel
        );
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        String normalized = phone.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
