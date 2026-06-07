package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateCashSaleItemRequest;
import com.gods.saas.domain.dto.request.CreateCashSaleRequest;
import com.gods.saas.domain.dto.request.CreatePublicProductOrderRequest;
import com.gods.saas.domain.dto.request.SalePaymentRequest;
import com.gods.saas.domain.dto.response.ProductOrderResponse;
import com.gods.saas.domain.dto.response.ProductResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.CashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductOrderService {

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final ProductBranchStockRepository productBranchStockRepository;
    private final ProductOrderRepository productOrderRepository;
    private final CashSaleService cashSaleService;

    @Transactional(readOnly = true)
    public List<ProductResponse> publicProducts(String codigoNegocio, Long branchId) {
        Tenant tenant = findTenant(codigoNegocio);
        return publicProductsByTenant(tenant.getId(), branchId);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> publicProductsByTenant(Long tenantId, Long branchId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Negocio no disponible"));
        Branch branch = resolveBranch(tenant.getId(), branchId);

        return productBranchStockRepository
                .findByTenantAndBranchWithProduct(tenant.getId(), branch.getId(), true)
                .stream()
                .filter(stock -> Boolean.TRUE.equals(stock.getActivo()))
                .filter(stock -> Boolean.TRUE.equals(stock.getProduct().getPublicVisible()))
                .map(stock -> toProductResponse(stock.getProduct(), stock, branch.getId()))
                .toList();
    }

    public ProductOrderResponse createPublicOrder(String codigoNegocio, CreatePublicProductOrderRequest request) {
        Tenant tenant = findTenant(codigoNegocio);
        Branch branch = resolveBranch(tenant.getId(), request.getBranchId());
        Product product = productRepository.findByIdAndTenant_Id(request.getProductId(), tenant.getId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        ProductBranchStock stock = productBranchStockRepository
                .findByTenant_IdAndBranch_IdAndProduct_Id(tenant.getId(), branch.getId(), product.getId())
                .orElseThrow(() -> new RuntimeException("Producto no disponible en esta sede"));

        if (!Boolean.TRUE.equals(product.getActivo())
                || !Boolean.TRUE.equals(product.getPublicVisible())
                || !Boolean.TRUE.equals(stock.getActivo())) {
            throw new RuntimeException("Producto no disponible para clientes");
        }

        int quantity = request.getQuantity() == null || request.getQuantity() < 1 ? 1 : request.getQuantity();
        int currentStock = stock.getStockActual() == null ? 0 : stock.getStockActual();
        if (!Boolean.TRUE.equals(product.getPermiteVentaSinStock()) && currentStock < quantity) {
            throw new RuntimeException("Stock insuficiente. Disponible: " + currentStock);
        }

        String customerName = clean(request.getCustomerName());
        String customerPhone = cleanDigits(request.getCustomerPhone());
        if (customerName == null) throw new RuntimeException("Ingresa tu nombre");
        if (customerPhone == null || customerPhone.length() < 6) throw new RuntimeException("Ingresa un telefono valido");

        BigDecimal unitPrice = resolvePrice(product);
        ProductOrder order = ProductOrder.builder()
                .tenant(tenant)
                .branch(branch)
                .product(product)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .total(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .paymentMethod(normalizePayment(request.getPaymentMethod()))
                .paymentOperationNumber(clean(request.getPaymentOperationNumber()))
                .paymentCaptureUrl(clean(request.getPaymentCaptureUrl()))
                .notes(clean(request.getNotes()))
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        return toResponse(productOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<ProductOrderResponse> ownerOrders(Long tenantId, Long branchId, String status) {
        List<ProductOrder> orders = clean(status) == null
                ? productOrderRepository.findByTenant_IdAndBranch_IdOrderByCreatedAtDesc(tenantId, branchId)
                : productOrderRepository.findByTenant_IdAndBranch_IdAndStatusOrderByCreatedAtDesc(
                        tenantId,
                        branchId,
                        status.trim().toUpperCase()
                );
        return orders.stream().map(this::toResponse).toList();
    }

    public ProductOrderResponse approve(Long tenantId, Long branchId, Long orderId, String note) {
        ProductOrder order = findOwnerOrder(tenantId, branchId, orderId);
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("Solo puedes aprobar pedidos pendientes");
        }
        order.setStatus("APPROVED");
        order.setAdminNote(clean(note));
        order.setValidatedAt(LocalDateTime.now());
        return toResponse(productOrderRepository.save(order));
    }

    public ProductOrderResponse reject(Long tenantId, Long branchId, Long orderId, String note) {
        ProductOrder order = findOwnerOrder(tenantId, branchId, orderId);
        if ("DELIVERED".equals(order.getStatus())) {
            throw new RuntimeException("No puedes rechazar un pedido entregado");
        }
        order.setStatus("REJECTED");
        order.setAdminNote(clean(note));
        order.setValidatedAt(LocalDateTime.now());
        return toResponse(productOrderRepository.save(order));
    }

    public ProductOrderResponse cancel(Long tenantId, Long branchId, Long orderId, String note) {
        ProductOrder order = findOwnerOrder(tenantId, branchId, orderId);
        if ("DELIVERED".equals(order.getStatus())) {
            throw new RuntimeException("No puedes cancelar un pedido entregado");
        }
        order.setStatus("CANCELLED");
        order.setAdminNote(clean(note));
        return toResponse(productOrderRepository.save(order));
    }

    public ProductOrderResponse deliver(Long tenantId, Long branchId, Long userId, Long orderId) {
        ProductOrder order = findOwnerOrder(tenantId, branchId, orderId);
        if ("REJECTED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("No puedes entregar un pedido rechazado o cancelado");
        }
        if ("DELIVERED".equals(order.getStatus())) {
            return toResponse(order);
        }

        Product product = order.getProduct();
        ProductBranchStock stock = productBranchStockRepository
                .findByTenant_IdAndBranch_IdAndProduct_Id(tenantId, branchId, product.getId())
                .orElseThrow(() -> new RuntimeException("Producto no disponible en esta sede"));
        int currentStock = stock.getStockActual() == null ? 0 : stock.getStockActual();
        int quantity = order.getQuantity() == null ? 1 : order.getQuantity();
        if (!Boolean.TRUE.equals(product.getPermiteVentaSinStock()) && currentStock < quantity) {
            throw new RuntimeException("Stock insuficiente. Disponible: " + currentStock);
        }

        CreateCashSaleItemRequest item = new CreateCashSaleItemRequest();
        item.setProductId(product.getId());
        item.setCantidad(quantity);
        item.setPrecioUnitario(order.getUnitPrice());

        CreateCashSaleRequest saleRequest = new CreateCashSaleRequest();
        saleRequest.setMetodoPago(toSalePaymentMethod(order.getPaymentMethod()));
        saleRequest.setCashReceived(order.getTotal());
        saleRequest.setItems(List.of(item));
        saleRequest.setCreatedByRole("OWNER");

        if (!"PAY_AT_SHOP".equals(order.getPaymentMethod())) {
            SalePaymentRequest payment = new SalePaymentRequest();
            payment.setMethod(toSalePaymentMethod(order.getPaymentMethod()));
            payment.setAmount(order.getTotal());
            saleRequest.setPayments(List.of(payment));
        }

        SaleResponse sale = cashSaleService.createCashSale(tenantId, branchId, userId, saleRequest);

        order.setSaleId(sale.getSaleId());
        order.setStatus("DELIVERED");
        order.setDeliveredAt(LocalDateTime.now());
        if (order.getValidatedAt() == null) order.setValidatedAt(LocalDateTime.now());
        return toResponse(productOrderRepository.save(order));
    }

    private ProductOrder findOwnerOrder(Long tenantId, Long branchId, Long orderId) {
        ProductOrder order = productOrderRepository.findByIdAndTenant_Id(orderId, tenantId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
        if (order.getBranch() == null || !order.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("El pedido no pertenece a esta sede");
        }
        return order;
    }

    private Tenant findTenant(String codigoNegocio) {
        String code = clean(codigoNegocio);
        if (code == null) throw new RuntimeException("Codigo de negocio requerido");
        return tenantRepository.findByCodigoIgnoreCaseAndActiveTrue(code)
                .orElseThrow(() -> new RuntimeException("Negocio no disponible"));
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

    private ProductResponse toProductResponse(Product product, ProductBranchStock stock, Long branchId) {
        int currentStock = stock.getStockActual() == null ? 0 : stock.getStockActual();
        int minStock = stock.getStockMinimo() == null ? 0 : stock.getStockMinimo();
        boolean available = Boolean.TRUE.equals(product.getPermiteVentaSinStock()) || currentStock > 0;
        return ProductResponse.builder()
                .id(product.getId())
                .branchId(branchId)
                .nombre(product.getNombre())
                .sku(product.getSku())
                .descripcion(product.getDescripcion())
                .precioCompra(product.getPrecioCompra())
                .precioVenta(resolvePrice(product))
                .precio(product.getPrecio())
                .barberCommissionAmount(product.getBarberCommissionAmount())
                .stockActual(currentStock)
                .stockMinimo(minStock)
                .categoria(product.getCategoria())
                .imageUrl(product.getImageUrl())
                .imagePublicId(product.getImagePublicId())
                .activo(Boolean.TRUE.equals(product.getActivo()) && Boolean.TRUE.equals(stock.getActivo()))
                .permiteVentaSinStock(Boolean.TRUE.equals(product.getPermiteVentaSinStock()))
                .stockBajo(currentStock <= minStock)
                .publicVisible(Boolean.TRUE.equals(product.getPublicVisible()))
                .publicFeatured(Boolean.TRUE.equals(product.getPublicFeatured()))
                .publicAvailable(available)
                .build();
    }

    private ProductOrderResponse toResponse(ProductOrder order) {
        Product product = order.getProduct();
        Branch branch = order.getBranch();
        return ProductOrderResponse.builder()
                .id(order.getId())
                .tenantId(order.getTenant() != null ? order.getTenant().getId() : null)
                .branchId(branch != null ? branch.getId() : null)
                .branchName(branch != null ? branch.getNombre() : null)
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getNombre() : "Producto")
                .productImageUrl(product != null ? product.getImageUrl() : null)
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .total(order.getTotal())
                .paymentMethod(order.getPaymentMethod())
                .paymentOperationNumber(order.getPaymentOperationNumber())
                .paymentCaptureUrl(order.getPaymentCaptureUrl())
                .status(order.getStatus())
                .statusLabel(statusLabel(order.getStatus()))
                .notes(order.getNotes())
                .adminNote(order.getAdminNote())
                .saleId(order.getSaleId())
                .createdAt(toText(order.getCreatedAt()))
                .updatedAt(toText(order.getUpdatedAt()))
                .validatedAt(toText(order.getValidatedAt()))
                .deliveredAt(toText(order.getDeliveredAt()))
                .expiresAt(toText(order.getExpiresAt()))
                .build();
    }

    private BigDecimal resolvePrice(Product product) {
        if (product.getPrecioVenta() != null && product.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0) {
            return product.getPrecioVenta();
        }
        return product.getPrecio() == null ? BigDecimal.ZERO : BigDecimal.valueOf(product.getPrecio());
    }

    private String normalizePayment(String value) {
        String code = clean(value);
        if (code == null) return "PAY_AT_SHOP";
        code = code.toUpperCase();
        if ("CASH".equals(code) || "EFECTIVO".equals(code) || "PAY_AT_SHOP".equals(code)) return "PAY_AT_SHOP";
        if ("YAPE".equals(code)) return "YAPE";
        if ("PLIN".equals(code)) return "PLIN";
        if ("TRANSFER".equals(code) || "TRANSFERENCIA".equals(code)) return "TRANSFER";
        return "PAY_AT_SHOP";
    }

    private String toSalePaymentMethod(String value) {
        return "PAY_AT_SHOP".equals(value) ? "CASH" : value;
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "APPROVED" -> "Aprobado";
            case "REJECTED" -> "Rechazado";
            case "DELIVERED" -> "Entregado";
            case "CANCELLED" -> "Cancelado";
            default -> "Pendiente";
        };
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String cleanDigits(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    private String toText(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
