package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateSaleFromAppointmentRequest;
import com.gods.saas.domain.dto.request.CreateSaleRequest;
import com.gods.saas.domain.dto.request.SaleItemRequest;
import com.gods.saas.domain.dto.response.CreateSaleFromAppointmentResponse;
import com.gods.saas.domain.dto.response.ProductResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.dto.response.SimpleServiceResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.Product;
import com.gods.saas.domain.model.ProductBranchStock;
import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.ProductRepository;
import com.gods.saas.domain.repository.ProductBranchStockRepository;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.service.impl.impl.SaleService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarberSaleService {

    private final AppointmentRepository appointmentRepository;
    private final ProductRepository productRepository;
    private final ProductBranchStockRepository productBranchStockRepository;
    private final ServiceRepository serviceRepository;
    private final SaleService saleService;
    private final CustomerCutHistoryService customerCutHistoryService;

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<SimpleServiceResponse> getAvailableServices(Long tenantId) {
        return serviceRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .map(service -> SimpleServiceResponse.builder()
                        .id(service.getId())
                        .nombre(service.getNombre())
                        .precio(service.getPrecio() == null
                                ? BigDecimal.ZERO
                                : BigDecimal.valueOf(service.getPrecio()).setScale(2, RoundingMode.HALF_UP))
                        .activo(service.getActivo())
                        .imageUrl(service.getImageUrl())
                        .build())
                .toList();
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ProductResponse> getAvailableProducts(Long tenantId, Long branchId) {
        List<ProductBranchStock> branchStocks = productBranchStockRepository
                .findByTenantAndBranchWithProduct(tenantId, branchId, true);

        if (!branchStocks.isEmpty()) {
            return branchStocks.stream()
                    .filter(stock -> Boolean.TRUE.equals(stock.getActivo()))
                    .map(stock -> toProductResponse(stock.getProduct(), stock))
                    .toList();
        }

        // Fallback mientras se migra product_branch_stock.
        return productRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .map(product -> toProductResponse(product, null))
                .toList();
    }

    private ProductResponse toProductResponse(Product product, ProductBranchStock branchStock) {
        int stockActual = branchStock != null && branchStock.getStockActual() != null
                ? branchStock.getStockActual()
                : (product.getStockActual() == null ? 0 : product.getStockActual());
        int stockMinimo = branchStock != null && branchStock.getStockMinimo() != null
                ? branchStock.getStockMinimo()
                : (product.getStockMinimo() == null ? 0 : product.getStockMinimo());

        BigDecimal precioVenta = product.getPrecioVenta() != null
                && product.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0
                ? product.getPrecioVenta()
                : (product.getPrecio() != null
                ? BigDecimal.valueOf(product.getPrecio())
                : BigDecimal.ZERO);

        return ProductResponse.builder()
                .id(product.getId())
                .branchId(branchStock != null && branchStock.getBranch() != null ? branchStock.getBranch().getId() : null)
                .nombre(product.getNombre())
                .sku(product.getSku())
                .descripcion(product.getDescripcion())
                .precioCompra(product.getPrecioCompra() == null ? BigDecimal.ZERO : product.getPrecioCompra())
                .precioVenta(precioVenta)
                .precio(product.getPrecio())
                .barberCommissionAmount(product.getBarberCommissionAmount() == null
                        ? BigDecimal.ZERO
                        : product.getBarberCommissionAmount())
                .stockActual(stockActual)
                .stockMinimo(stockMinimo)
                .categoria(product.getCategoria())
                .activo(Boolean.TRUE.equals(product.getActivo()))
                .permiteVentaSinStock(Boolean.TRUE.equals(product.getPermiteVentaSinStock()))
                .stockBajo(stockActual <= stockMinimo)
                .imageUrl(product.getImageUrl())
                .build();
    }

    @Transactional
    public CreateSaleFromAppointmentResponse createSaleFromAppointment(
            Long tenantId,
            Long branchId,
            Long userId,
            CreateSaleFromAppointmentRequest request
    ) {
        if (request.getAppointmentId() == null) {
            throw new RuntimeException("appointmentId es obligatorio");
        }

        boolean hasMixedPayments = request.getPayments() != null && !request.getPayments().isEmpty();

        if (!hasMixedPayments && (request.getMetodoPago() == null || request.getMetodoPago().isBlank())) {
            throw new RuntimeException("metodoPago es obligatorio");
        }

        String metodoPago = hasMixedPayments ? "MIXED" : normalizePaymentMethod(request.getMetodoPago());

        if (!hasMixedPayments && !isValidPaymentMethod(metodoPago)) {
            throw new RuntimeException("metodoPago no válido");
        }

        Appointment appointment = appointmentRepository
                .findByIdAndTenant_IdAndBranch_IdAndUser_Id(
                        request.getAppointmentId(),
                        tenantId,
                        branchId,
                        userId
                )
                .orElseThrow(() -> new RuntimeException("No se encontró la cita"));

        String estadoActual = normalize(appointment.getEstado());

        if ("ATENDIDO".equals(estadoActual)
                || "COMPLETADO".equals(estadoActual)
                || "FINALIZADO".equals(estadoActual)) {
            throw new RuntimeException("La cita ya fue atendida");
        }

        ServiceEntity service = appointment.getService();

        if (service == null) {
            throw new RuntimeException("La cita no tiene servicio asociado");
        }

        if (service.getId() == null) {
            throw new RuntimeException("El servicio de la cita no es válido");
        }

        if (service.getPrecio() == null) {
            throw new RuntimeException("El servicio no tiene precio configurado");
        }

        BigDecimal serviceTotal = BigDecimal.valueOf(service.getPrecio())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tipAmount = request.getTipAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : request.getTipAmount().setScale(2, RoundingMode.HALF_UP);

        if (tipAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("La propina no puede ser negativa");
        }

        BigDecimal depositApplied = resolveApprovedDepositAmount(appointment, serviceTotal);

        List<SaleItemRequest> saleItems = new ArrayList<>();

        SaleItemRequest serviceItem = new SaleItemRequest();
        serviceItem.setServiceId(service.getId());
        serviceItem.setBarberUserId(userId);
        serviceItem.setCantidad(1);
        serviceItem.setPrecioUnitario(service.getPrecio());
        saleItems.add(serviceItem);

        BigDecimal productsTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (request.getItems() != null) {
            for (SaleItemRequest extra : request.getItems()) {
                if (extra == null || extra.getProductId() == null) {
                    continue;
                }

                Product product = productRepository.findByIdAndTenant_Id(extra.getProductId(), tenantId)
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

                if (!Boolean.TRUE.equals(product.getActivo())) {
                    throw new RuntimeException("El producto " + product.getNombre() + " está inactivo");
                }

                int cantidad = extra.getCantidad() == null || extra.getCantidad() <= 0
                        ? 1
                        : extra.getCantidad();

                if (!Boolean.TRUE.equals(product.getPermiteVentaSinStock())) {
                    int stock = productBranchStockRepository
                            .findByTenant_IdAndBranch_IdAndProduct_Id(tenantId, branchId, product.getId())
                            .map(ProductBranchStock::getStockActual)
                            .orElse(product.getStockActual() == null ? 0 : product.getStockActual());

                    if (stock < cantidad) {
                        throw new RuntimeException("Stock insuficiente para " + product.getNombre());
                    }
                }

                BigDecimal unitPrice = product.getPrecioVenta() != null
                        && product.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0
                        ? product.getPrecioVenta()
                        : (product.getPrecio() != null
                        ? BigDecimal.valueOf(product.getPrecio())
                        : BigDecimal.ZERO);

                unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);

                SaleItemRequest productItem = new SaleItemRequest();
                productItem.setProductId(product.getId());
                productItem.setBarberUserId(userId);
                productItem.setCantidad(cantidad);
                productItem.setPrecioUnitario(unitPrice.doubleValue());
                saleItems.add(productItem);

                productsTotal = productsTotal
                        .add(unitPrice.multiply(BigDecimal.valueOf(cantidad)))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal fullTotal = serviceTotal
                .add(productsTotal)
                .add(tipAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountToCollectNow = fullTotal
                .subtract(depositApplied)
                .setScale(2, RoundingMode.HALF_UP);

        if (amountToCollectNow.compareTo(BigDecimal.ZERO) < 0) {
            amountToCollectNow = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal cashReceived = null;
        BigDecimal change = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (!hasMixedPayments && "EFECTIVO".equals(metodoPago)) {
            if (request.getCashReceived() == null) {
                throw new RuntimeException("Para pago en efectivo debes enviar cashReceived");
            }

            cashReceived = BigDecimal.valueOf(request.getCashReceived())
                    .setScale(2, RoundingMode.HALF_UP);

            if (cashReceived.compareTo(amountToCollectNow) < 0) {
                throw new RuntimeException("El monto recibido no puede ser menor al saldo pendiente");
            }

            change = cashReceived.subtract(amountToCollectNow)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        CreateSaleRequest saleRequest = new CreateSaleRequest();
        saleRequest.setTenantId(tenantId);
        saleRequest.setBranchId(branchId);
        saleRequest.setCustomerId(
                appointment.getCustomer() != null ? appointment.getCustomer().getId() : null
        );
        saleRequest.setUserId(userId);
        saleRequest.setAppointmentId(appointment.getId());
        saleRequest.setMetodoPago(metodoPago);
        saleRequest.setDiscount(BigDecimal.ZERO);
        saleRequest.setCashReceived(cashReceived);
        saleRequest.setTipAmount(tipAmount);
        saleRequest.setTipBarberUserId(userId);
        saleRequest.setPayments(request.getPayments());
        saleRequest.setCutType(request.getCutType());
        saleRequest.setCutDetail(request.getCutDetail());
        saleRequest.setCutObservations(request.getCutObservations());
        saleRequest.setItems(saleItems);

        SaleResponse saleResponse = saleService.crearVenta(saleRequest);

        appointment.setEstado("COMPLETADO");
        appointment.setRemainingAmount(amountToCollectNow);

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String notes = request.getNotes().trim();
            appointment.setNotas(notes);
            customerCutHistoryService.syncNotesFromSale(tenantId, saleResponse.getSaleId(), notes);
        }

        appointmentRepository.save(appointment);

        Customer customer = appointment.getCustomer();

        int pointsEarned = saleResponse.getPuntosGanados() != null ? saleResponse.getPuntosGanados() : 0;
        int customerPointsBalance = saleResponse.getPuntosDisponibles() != null ? saleResponse.getPuntosDisponibles() : 0;

        String clienteNombre = "Sin cliente";

        if (customer != null) {
            String nombre = customer.getNombres() != null ? customer.getNombres().trim() : "";
            String apellido = customer.getApellidos() != null ? customer.getApellidos().trim() : "";
            String fullName = (nombre + " " + apellido).trim();

            if (!fullName.isBlank()) {
                clienteNombre = fullName;
            }
        }

        BigDecimal finalDepositApplied = saleResponse.getDepositApplied() == null
                ? depositApplied
                : saleResponse.getDepositApplied();

        BigDecimal finalAmountToCollectNow = saleResponse.getAmountToCollectNow() == null
                ? amountToCollectNow
                : saleResponse.getAmountToCollectNow();

        CreateSaleFromAppointmentResponse response = new CreateSaleFromAppointmentResponse();
        response.setSuccess(true);
        response.setMessage("Venta registrada correctamente");
        response.setSaleId(saleResponse.getSaleId());
        response.setAppointmentId(appointment.getId());
        response.setCliente(clienteNombre);
        response.setServicio(service.getNombre() != null ? service.getNombre() : "Servicio");
        response.setMetodoPago(metodoPago);

        response.setTotal(
                saleResponse.getTotal() != null
                        ? saleResponse.getTotal().doubleValue()
                        : fullTotal.doubleValue()
        );

        response.setDepositApplied(finalDepositApplied.doubleValue());
        response.setAmountToCollectNow(finalAmountToCollectNow.doubleValue());
        response.setDepositStatus(appointment.getDepositStatus());
        response.setDepositMethodCode(appointment.getDepositMethodCode());
        response.setDepositMethodName(appointment.getDepositMethodName());

        response.setCashReceived(
                saleResponse.getCashReceived() != null
                        ? saleResponse.getCashReceived().doubleValue()
                        : (cashReceived != null ? cashReceived.doubleValue() : null)
        );

        response.setChange(
                saleResponse.getChangeAmount() != null
                        ? saleResponse.getChangeAmount().doubleValue()
                        : change.doubleValue()
        );

        response.setPointsEarned(pointsEarned);
        response.setCustomerPointsBalance(customerPointsBalance);
        response.setFechaHora(LocalDateTime.now().toString());

        return response;
    }

    private BigDecimal resolveApprovedDepositAmount(Appointment appointment, BigDecimal serviceTotal) {
        if (!Boolean.TRUE.equals(appointment.getDepositRequired())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String depositStatus = normalize(appointment.getDepositStatus());

        if ("PENDING_VALIDATION".equals(depositStatus)) {
            throw new RuntimeException("La reserva tiene un pago inicial pendiente de validación");
        }

        if ("REJECTED".equals(depositStatus)) {
            throw new RuntimeException("El pago inicial de esta reserva fue rechazado");
        }

        if (!"PAID".equals(depositStatus)) {
            throw new RuntimeException("La reserva requiere pago inicial aprobado");
        }

        BigDecimal depositAmount = appointment.getDepositAmount() == null
                ? BigDecimal.ZERO
                : appointment.getDepositAmount();

        depositAmount = depositAmount.setScale(2, RoundingMode.HALF_UP);

        if (depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (depositAmount.compareTo(serviceTotal) > 0) {
            return serviceTotal;
        }

        return depositAmount;
    }

    private boolean isValidPaymentMethod(String metodoPago) {
        String method = normalizePaymentMethod(metodoPago);

        return "EFECTIVO".equals(method)
                || "TARJETA".equals(method)
                || "YAPE".equals(method)
                || "PLIN".equals(method)
                || "TRANSFER".equals(method)
                || "NEQUI".equals(method)
                || "DAVIPLATA".equals(method)
                || "PAGO_MOVIL".equals(method)
                || "ZELLE".equals(method)
                || "QR".equals(method)
                || "FREE".equals(method)
                || "OTHER".equals(method);
    }

    private String normalizePaymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return "EFECTIVO";
        }

        return switch (value.trim().toUpperCase()) {
            case "CASH", "EFECTIVO" -> "EFECTIVO";
            case "CARD", "TARJETA" -> "TARJETA";
            case "TRANSFER", "TRANSFERENCIA" -> "TRANSFER";
            case "PAGO MOVIL", "PAGO_MÓVIL", "PAGO_MOVIL" -> "PAGO_MOVIL";
            case "FREE", "GRATIS", "CORTESIA", "CORTESÍA" -> "FREE";
            default -> value.trim().toUpperCase();
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}