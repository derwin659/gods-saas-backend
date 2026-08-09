package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberSaleReviewResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.model.SaleItem;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BarberSaleReviewService {
    private final SaleRepository saleRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public BarberSaleReviewResponse getReviewHistory(Authentication authentication) {
        AppUser barber = currentUser(authentication);
        Long tenantId = currentTenantId(authentication);
        List<Sale> sales = saleRepository.findBarberSaleReviewIds(tenantId, barber.getId()).stream()
                .map(id -> saleRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<BarberSaleReviewResponse.Item> items = sales.stream().map(this::toItem).toList();
        return BarberSaleReviewResponse.builder()
                .pendingCount(items.stream().filter(i -> "PENDING".equals(i.getStatus())).count())
                .approvedCount(items.stream().filter(i -> "APPROVED".equals(i.getStatus())).count())
                .rejectedCount(items.stream().filter(i -> "REJECTED".equals(i.getStatus())).count())
                .items(items).build();
    }

    private BarberSaleReviewResponse.Item toItem(Sale sale) {
        List<String> services = sale.getItems().stream()
                .filter(i -> "SERVICE".equalsIgnoreCase(i.getTipoItem()) || i.getService() != null)
                .map(SaleItem::getNombreItem).filter(name -> name != null && !name.isBlank()).distinct().toList();
        return BarberSaleReviewResponse.Item.builder()
                .saleId(sale.getId()).status(normalizeStatus(sale.getPaymentValidationStatus()))
                .customerName(customerName(sale.getCustomer()))
                .branchName(sale.getBranch() == null ? "Sede" : sale.getBranch().getNombre())
                .services(services).total(sale.getTotal())
                .registeredAt(sale.getSaleDate() != null ? sale.getSaleDate() : sale.getFechaCreacion())
                .reviewedAt(sale.getValidatedAt()).reviewedBy(userName(sale.getValidatedByUser()))
                .rejectionReason(sale.getRejectionReason()).build();
    }

    private String normalizeStatus(String value) {
        String status = value == null ? "APPROVED" : value.trim().toUpperCase();
        if (status.equals("PENDING_VALIDATION") || status.equals("PENDING")) return "PENDING";
        if (status.equals("REJECTED") || status.equals("RECHAZADO")) return "REJECTED";
        return "APPROVED";
    }

    private String customerName(Customer customer) {
        if (customer == null) return "Cliente sin identificar";
        String full = ((customer.getNombres() == null ? "" : customer.getNombres()) + " " +
                (customer.getApellidos() == null ? "" : customer.getApellidos())).trim();
        return full.isBlank() ? "Cliente" : full;
    }

    private String userName(AppUser user) {
        if (user == null) return null;
        String full = ((user.getNombre() == null ? "" : user.getNombre()) + " " +
                (user.getApellido() == null ? "" : user.getApellido())).trim();
        return full.isBlank() ? "Administración" : full;
    }

    private AppUser currentUser(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) throw new RuntimeException("Usuario no autenticado");
        return appUserRepository.findById(Long.valueOf(auth.getPrincipal().toString()))
                .orElseThrow(() -> new RuntimeException("Profesional no encontrado"));
    }

    private Long currentTenantId(Authentication auth) {
        if (auth == null || !(auth.getDetails() instanceof Map<?, ?> details)) throw new RuntimeException("No se pudo obtener el tenant");
        Object value = details.get("tenantId");
        if (value == null) throw new RuntimeException("tenantId no encontrado");
        return Long.valueOf(value.toString());
    }
}
