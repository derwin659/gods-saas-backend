package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.CustomerCutHistoryResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.CustomerCutHistory;
import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.model.SaleItem;
import com.gods.saas.domain.repository.CustomerCutHistoryRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.jsonwebtoken.lang.Strings.clean;

@Service
@RequiredArgsConstructor
public class CustomerCutHistoryService {

    private final CustomerCutHistoryRepository customerCutHistoryRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public void registerFromSale(Sale sale, String cutType,
                                 String cutDetail,
                                 String cutObservations) {
        if (sale == null || sale.getId() == null || sale.getCustomer() == null) {
            return;
        }

        List<SaleItem> serviceItems = sale.getItems() == null
                ? List.of()
                : sale.getItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getService() != null)
                .toList();

        if (serviceItems.isEmpty()) {
            return;
        }

        CustomerCutHistory history = customerCutHistoryRepository
                .findTopByTenant_IdAndSale_Id(sale.getTenant().getId(), sale.getId())
                .orElseGet(CustomerCutHistory::new);

        history.setTenant(sale.getTenant());
        history.setBranch(sale.getBranch());
        history.setCustomer(sale.getCustomer());
        history.setBarberUser(resolvePrincipalBarber(sale, serviceItems));
        history.setAppointment(sale.getAppointment());
        history.setSale(sale);
        history.setCutName(buildCutName(serviceItems));
        history.setCutDescription(buildCutDescription(serviceItems));
        history.setObservations(resolveObservations(sale));
        history.setFechaCorte(sale.getFechaCreacion() != null ? sale.getFechaCreacion() : LocalDateTime.now());
        history.setCutName(clean(cutType));
        history.setCutDetail(clean(cutDetail));
        history.setObservations(clean(cutObservations));
        customerCutHistoryRepository.save(history);
    }

    @Transactional
    public void syncNotesFromSale(Long tenantId, Long saleId, String notes) {
        if (tenantId == null || saleId == null || notes == null || notes.isBlank()) {
            return;
        }

        customerCutHistoryRepository
                .findTopByTenant_IdAndSale_Id(tenantId, saleId)
                .ifPresent(history -> {
                    history.setObservations(notes.trim());
                    customerCutHistoryRepository.save(history);
                });
    }

    @Transactional(readOnly = true)
    public List<CustomerCutHistoryResponse> listByCustomer(Long tenantId, Long customerId, int limit) {
        validateCustomer(tenantId, customerId);

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return customerCutHistoryRepository
                .findByTenant_IdAndCustomer_IdOrderByFechaCorteDescIdDesc(
                        tenantId,
                        customerId,
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(CustomerCutHistoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerCutHistoryResponse getLastByCustomer(Long tenantId, Long customerId) {
        validateCustomer(tenantId, customerId);

        return customerCutHistoryRepository
                .findTopByTenant_IdAndCustomer_IdOrderByFechaCorteDescIdDesc(tenantId, customerId)
                .map(CustomerCutHistoryResponse::fromEntity)
                .orElse(null);
    }

    private void validateCustomer(Long tenantId, Long customerId) {
        customerRepository.findByIdAndTenant_Id(customerId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    private AppUser resolvePrincipalBarber(Sale sale, List<SaleItem> serviceItems) {
        if (sale.getUser() != null) {
            return sale.getUser();
        }

        Optional<AppUser> firstItemBarber = serviceItems.stream()
                .map(SaleItem::getBarberUser)
                .filter(Objects::nonNull)
                .findFirst();

        return firstItemBarber.orElse(null);
    }

    private String buildCutName(List<SaleItem> serviceItems) {
        LinkedHashSet<String> names = serviceItems.stream()
                .map(SaleItem::getService)
                .filter(Objects::nonNull)
                .map(service -> service.getNombre() == null ? "" : service.getNombre().trim())
                .filter(name -> !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (names.isEmpty()) {
            return "Servicio realizado";
        }

        return String.join(" + ", names);
    }

    private String buildCutDescription(List<SaleItem> serviceItems) {
        return serviceItems.stream()
                .map(item -> {
                    String serviceName = item.getService() != null ? item.getService().getNombre() : null;
                    String barberName = item.getBarberUser() != null ? item.getBarberUser().getNombre() : null;

                    StringBuilder sb = new StringBuilder();
                    if (serviceName != null && !serviceName.isBlank()) {
                        sb.append(serviceName.trim());
                    }
                    if (barberName != null && !barberName.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append(" - ");
                        }
                        sb.append("Barbero: ").append(barberName.trim());
                    }
                    return sb.toString().trim();
                })
                .filter(text -> !text.isBlank())
                .distinct()
                .collect(Collectors.joining(" | "));
    }

    private String resolveObservations(Sale sale) {
        if (sale.getAppointment() != null
                && sale.getAppointment().getNotas() != null
                && !sale.getAppointment().getNotas().isBlank()) {
            return sale.getAppointment().getNotas().trim();
        }
        return null;
    }
}
