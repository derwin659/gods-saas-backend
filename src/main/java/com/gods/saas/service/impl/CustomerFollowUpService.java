package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CustomerFollowUpRequest;
import com.gods.saas.domain.dto.response.CustomerFollowUpResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.CustomerFollowUp;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.CustomerFollowUpRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerFollowUpService {

    private final CustomerFollowUpRepository followUpRepository;
    private final CustomerRepository customerRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public List<CustomerFollowUpResponse> list(Long tenantId, Long customerId) {
        ensureCustomer(tenantId, customerId);
        return followUpRepository
                .findTop50ByTenant_IdAndCustomer_IdOrderByCreatedAtDesc(tenantId, customerId)
                .stream()
                .map(CustomerFollowUpResponse::from)
                .toList();
    }

    @Transactional
    public CustomerFollowUpResponse create(Long tenantId, Long customerId, Long actorUserId, CustomerFollowUpRequest request) {
        if (request == null) throw new RuntimeException("Solicitud invalida");
        String message = clean(request.getMessage());
        if (message == null) throw new RuntimeException("Escribe el mensaje del seguimiento");
        String title = clean(request.getTitle());
        if (title == null) title = "Seguimiento de cliente";

        Customer customer = ensureCustomer(tenantId, customerId);
        AppUser actor = actorUserId == null ? null : appUserRepository.findByIdAndTenant_Id(actorUserId, tenantId).orElse(null);

        CustomerFollowUp item = CustomerFollowUp.builder()
                .tenant(customer.getTenant())
                .customer(customer)
                .actorUser(actor)
                .title(limit(title, 160))
                .message(limit(message, 800))
                .channel(normalizeChannel(request.getChannel()))
                .status("PENDING")
                .scheduledAt(request.getScheduledAt())
                .build();

        return CustomerFollowUpResponse.from(followUpRepository.save(item));
    }

    @Transactional
    public CustomerFollowUpResponse updateStatus(Long tenantId, Long customerId, Long followUpId, String status) {
        CustomerFollowUp item = followUpRepository
                .findByIdAndTenant_IdAndCustomer_Id(followUpId, tenantId, customerId)
                .orElseThrow(() -> new RuntimeException("Seguimiento no encontrado"));

        String normalized = normalizeStatus(status);
        item.setStatus(normalized);
        if ("DONE".equals(normalized) || "SENT".equals(normalized) || "SKIPPED".equals(normalized) || "FAILED".equals(normalized)) {
            item.setCompletedAt(LocalDateTime.now());
        } else if ("PENDING".equals(normalized)) {
            item.setCompletedAt(null);
        }

        return CustomerFollowUpResponse.from(followUpRepository.save(item));
    }

    private Customer ensureCustomer(Long tenantId, Long customerId) {
        return customerRepository.findByIdAndTenant_IdAndActivoTrue(customerId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    private String normalizeChannel(String value) {
        String clean = clean(value);
        if (clean == null) return "WHATSAPP";
        String upper = clean.toUpperCase();
        if (!upper.equals("WHATSAPP") && !upper.equals("PUSH") && !upper.equals("BOTH") && !upper.equals("WHATSAPP_PUSH") && !upper.equals("PHONE") && !upper.equals("MANUAL")) {
            return "WHATSAPP";
        }
        return "WHATSAPP_PUSH".equals(upper) ? "BOTH" : upper;
    }

    private String normalizeStatus(String value) {
        String clean = clean(value);
        if (clean == null) return "PENDING";
        String upper = clean.toUpperCase();
        if (!upper.equals("PENDING") && !upper.equals("DONE") && !upper.equals("SENT") && !upper.equals("CANCELLED") && !upper.equals("SKIPPED") && !upper.equals("FAILED")) {
            throw new RuntimeException("Estado de seguimiento invalido");
        }
        return upper;
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
