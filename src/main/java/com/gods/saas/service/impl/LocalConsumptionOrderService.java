package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateLocalConsumptionOrderRequest;
import com.gods.saas.domain.dto.request.CreateCashSaleItemRequest;
import com.gods.saas.domain.dto.request.CreateCashSaleRequest;
import com.gods.saas.domain.dto.response.LocalConsumptionOrderResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.CashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LocalConsumptionOrderService {
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final ServiceRepository serviceRepository;
    private final ProductRepository productRepository;
    private final ProductBranchStockRepository productBranchStockRepository;
    private final AppUserRepository appUserRepository;
    private final LocalConsumptionOrderRepository orderRepository;
    private final CashSaleService cashSaleService;

    public LocalConsumptionOrderResponse createClientOrder(Long tenantId, Long customerId, CreateLocalConsumptionOrderRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Negocio no disponible"));
        Customer customer = customerRepository.findByIdAndTenant_IdAndActivoTrue(customerId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        Branch branch = resolveBranch(tenantId, request.getBranchId());

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Selecciona al menos un servicio o producto");
        }

        LocalConsumptionOrder order = LocalConsumptionOrder.builder()
                .tenant(tenant)
                .branch(branch)
                .customer(customer)
                .customerName(buildCustomerName(customer))
                .customerPhone(clean(customer.getTelefono()))
                .status("PENDING")
                .notes(clean(request.getNotes()))
                .createdAt(LocalDateTime.now())
                .total(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .build();

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (CreateLocalConsumptionOrderRequest.Item rawItem : request.getItems()) {
            if (rawItem == null) continue;
            LocalConsumptionOrderItem item = buildItem(tenantId, branch.getId(), rawItem);
            total = total.add(item.getSubtotal()).setScale(2, RoundingMode.HALF_UP);
            order.addItem(item);
        }

        if (order.getItems().isEmpty()) {
            throw new RuntimeException("Selecciona al menos un servicio o producto");
        }

        order.setTotal(total);
        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<LocalConsumptionOrderResponse> ownerOrders(Long tenantId, Long branchId, String status) {
        String cleanStatus = clean(status);
        List<LocalConsumptionOrder> orders = cleanStatus == null
                ? orderRepository.findByTenant_IdAndBranch_IdOrderByCreatedAtDesc(tenantId, branchId)
                : orderRepository.findByTenant_IdAndBranch_IdAndStatusOrderByCreatedAtDesc(
                tenantId,
                branchId,
                cleanStatus.toUpperCase()
        );
        return orders.stream().map(this::toResponse).toList();
    }

    public LocalConsumptionOrderResponse reject(Long tenantId, Long branchId, Long orderId, String note) {
        LocalConsumptionOrder order = findOwnerOrder(tenantId, branchId, orderId);
        if ("COMPLETED".equals(order.getStatus())) {
            throw new RuntimeException("No puedes rechazar una solicitud ya completada");
        }
        order.setStatus("REJECTED");
        order.setAdminNote(clean(note));
        order.setHandledAt(LocalDateTime.now());
        return toResponse(orderRepository.save(order));
    }

    public LocalConsumptionOrderResponse complete(Long tenantId, Long branchId, Long userId, Long orderId, Long saleId, String paymentMethod) {
        LocalConsumptionOrder order = findOwnerOrder(tenantId, branchId, orderId);

        if ("REJECTED".equals(order.getStatus())) {
            throw new RuntimeException("No puedes completar una solicitud rechazada");
        }
        if ("COMPLETED".equals(order.getStatus())) {
            return toResponse(order);
        }

        Long resolvedSaleId = saleId;
        if (resolvedSaleId == null) {
            SaleResponse sale = cashSaleService.createCashSale(
                    tenantId,
                    branchId,
                    userId,
                    buildCashSaleRequest(order, paymentMethod)
            );
            resolvedSaleId = sale.getSaleId();
        }

        order.setStatus("COMPLETED");
        order.setSaleId(resolvedSaleId);
        order.setHandledAt(LocalDateTime.now());
        return toResponse(orderRepository.save(order));
    }

    private CreateCashSaleRequest buildCashSaleRequest(LocalConsumptionOrder order, String paymentMethod) {
        CreateCashSaleRequest request = new CreateCashSaleRequest();
        request.setCustomerId(order.getCustomer() != null ? order.getCustomer().getId() : null);
        request.setMetodoPago(resolvePaymentMethod(paymentMethod));
        request.setCashReceived(order.getTotal());
        request.setDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        request.setCreatedByRole("OWNER");
        request.setItems(order.getItems().stream().map(this::toCashSaleItem).toList());
        return request;
    }

    private String resolvePaymentMethod(String paymentMethod) {
        String clean = clean(paymentMethod);
        return clean == null ? "EFECTIVO" : clean.toUpperCase();
    }

    private CreateCashSaleItemRequest toCashSaleItem(LocalConsumptionOrderItem orderItem) {
        CreateCashSaleItemRequest item = new CreateCashSaleItemRequest();
        if ("PRODUCT".equalsIgnoreCase(orderItem.getItemType()) && orderItem.getProduct() != null) {
            item.setProductId(orderItem.getProduct().getId());
        } else if (orderItem.getService() != null) {
            item.setServiceId(orderItem.getService().getId());
        }
        if (orderItem.getBarberUser() != null) {
            item.setBarberUserId(orderItem.getBarberUser().getId());
        }
        item.setCantidad(orderItem.getQuantity() == null || orderItem.getQuantity() < 1 ? 1 : orderItem.getQuantity());
        item.setPrecioUnitario(orderItem.getUnitPrice());
        return item;
    }

    private LocalConsumptionOrderItem buildItem(Long tenantId, Long branchId, CreateLocalConsumptionOrderRequest.Item rawItem) {
        boolean hasService = rawItem.getServiceId() != null;
        boolean hasProduct = rawItem.getProductId() != null;

        if (hasService == hasProduct) {
            throw new RuntimeException("Cada item debe ser servicio o producto");
        }

        int quantity = rawItem.getQuantity() == null || rawItem.getQuantity() < 1 ? 1 : rawItem.getQuantity();
        BigDecimal unitPrice;
        String itemName;
        ServiceEntity service = null;
        Product product = null;

        if (hasService) {
            service = serviceRepository.findById(rawItem.getServiceId())
                    .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
            if (service.getTenant() == null || !service.getTenant().getId().equals(tenantId) || !Boolean.TRUE.equals(service.getActivo())) {
                throw new RuntimeException("Servicio no disponible");
            }
            itemName = service.getNombre();
            unitPrice = rawItem.getUnitPrice() != null && rawItem.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                    ? rawItem.getUnitPrice()
                    : BigDecimal.valueOf(service.getPrecio() == null ? 0.0 : service.getPrecio());
        } else {
            product = productRepository.findByIdAndTenant_Id(rawItem.getProductId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
            ProductBranchStock stock = productBranchStockRepository
                    .findByTenant_IdAndBranch_IdAndProduct_Id(tenantId, branchId, product.getId())
                    .orElseThrow(() -> new RuntimeException("Producto no disponible en esta sede"));
            int currentStock = stock.getStockActual() == null ? 0 : stock.getStockActual();
            if (!Boolean.TRUE.equals(product.getPermiteVentaSinStock()) && currentStock < quantity) {
                throw new RuntimeException("Stock insuficiente. Disponible: " + currentStock);
            }
            itemName = product.getNombre();
            unitPrice = product.getPrecioVenta() != null && product.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0
                    ? product.getPrecioVenta()
                    : BigDecimal.valueOf(product.getPrecio() == null ? 0.0 : product.getPrecio());
        }

        AppUser barber = null;
        if (rawItem.getBarberUserId() != null) {
            barber = appUserRepository.findByIdAndTenant_Id(rawItem.getBarberUserId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Profesional no encontrado"));
        }

        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        return LocalConsumptionOrderItem.builder()
                .itemType(hasService ? "SERVICE" : "PRODUCT")
                .service(service)
                .product(product)
                .barberUser(barber)
                .itemName(itemName == null || itemName.trim().isEmpty() ? (hasService ? "Servicio" : "Producto") : itemName.trim())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .notes(clean(rawItem.getNotes()))
                .build();
    }

    private LocalConsumptionOrder findOwnerOrder(Long tenantId, Long branchId, Long orderId) {
        LocalConsumptionOrder order = orderRepository.findByIdAndTenant_Id(orderId, tenantId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        if (order.getBranch() == null || !order.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("La solicitud no pertenece a esta sede");
        }
        return order;
    }

    private Branch resolveBranch(Long tenantId, Long branchId) {
        if (branchId != null) {
            return branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                    .filter(branch -> Boolean.TRUE.equals(branch.getActivo()))
                    .orElseThrow(() -> new RuntimeException("Sede no disponible"));
        }
        return branchRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No hay sedes disponibles"));
    }

    private LocalConsumptionOrderResponse toResponse(LocalConsumptionOrder order) {
        Branch branch = order.getBranch();
        Customer customer = order.getCustomer();
        return LocalConsumptionOrderResponse.builder()
                .id(order.getId())
                .tenantId(order.getTenant() != null ? order.getTenant().getId() : null)
                .branchId(branch != null ? branch.getId() : null)
                .branchName(branch != null ? branch.getNombre() : null)
                .customerId(customer != null ? customer.getId() : null)
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .status(order.getStatus())
                .statusLabel(statusLabel(order.getStatus()))
                .total(order.getTotal())
                .notes(order.getNotes())
                .adminNote(order.getAdminNote())
                .saleId(order.getSaleId())
                .createdAt(toText(order.getCreatedAt()))
                .updatedAt(toText(order.getUpdatedAt()))
                .handledAt(toText(order.getHandledAt()))
                .items(order.getItems().stream().map(this::toItemResponse).toList())
                .build();
    }

    private LocalConsumptionOrderResponse.Item toItemResponse(LocalConsumptionOrderItem item) {
        ServiceEntity service = item.getService();
        Product product = item.getProduct();
        AppUser barber = item.getBarberUser();
        return LocalConsumptionOrderResponse.Item.builder()
                .id(item.getId())
                .type(item.getItemType())
                .serviceId(service != null ? service.getId() : null)
                .serviceName(service != null ? service.getNombre() : null)
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getNombre() : null)
                .barberUserId(barber != null ? barber.getId() : null)
                .barberUserName(barber != null ? barber.getNombre() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .notes(item.getNotes())
                .build();
    }

    private String buildCustomerName(Customer customer) {
        String nombres = clean(customer.getNombres());
        String apellidos = clean(customer.getApellidos());
        String full = ((nombres == null ? "" : nombres) + " " + (apellidos == null ? "" : apellidos)).trim();
        return full.isBlank() ? "Cliente" : full;
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "COMPLETED" -> "Completado";
            case "REJECTED" -> "Rechazado";
            default -> "Pendiente";
        };
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toText(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
