package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateSaleRequest;
import com.gods.saas.domain.dto.request.SaleItemRequest;
import com.gods.saas.domain.dto.request.SalePaymentRequest;
import com.gods.saas.domain.dto.response.SaleItemResponse;
import com.gods.saas.domain.dto.response.SalePaymentResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.enums.CashRegisterStatus;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.LoyaltyService;
import com.gods.saas.service.impl.impl.NotificationService;
import com.gods.saas.service.impl.impl.SaleService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private static final int POINTS_PER_SOL = 5;

    private final LoyaltyService loyaltyService;
    private final SaleRepository saleRepository;
    private final ServiceRepository serviceRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final AppUserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TenantTimeService tenantTimeService;
    private final CustomerCutHistoryService customerCutHistoryService;
    private final NotificationService notificationService;

    @Override
    public SaleResponse crearVenta(CreateSaleRequest request) {

        validarRequest(request);

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        if (!branch.getTenant().getId().equals(tenant.getId())) {
            throw new RuntimeException("La sucursal no pertenece al tenant");
        }

        CashRegister cashRegister = cashRegisterRepository
                .findByTenant_IdAndBranch_IdAndStatus(tenant.getId(), branch.getId(), CashRegisterStatus.OPEN)
                .orElseThrow(() -> new RuntimeException("No hay una caja abierta en esta sede."));

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

            if (!customer.getTenant().getId().equals(tenant.getId())) {
                throw new RuntimeException("El cliente no pertenece al tenant");
            }
        }

        AppUser user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Barbero/usuario no encontrado"));

            if (!user.getTenant().getId().equals(tenant.getId())) {
                throw new RuntimeException("El usuario no pertenece al tenant");
            }
        }

        Appointment appointment = null;
        if (request.getAppointmentId() != null) {
            appointment = appointmentRepository.findById(request.getAppointmentId())
                    .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

            if (!appointment.getTenant().getId().equals(tenant.getId())) {
                throw new RuntimeException("La cita no pertenece al tenant");
            }

            if (appointment.getBranch() != null && !appointment.getBranch().getId().equals(branch.getId())) {
                throw new RuntimeException("La cita no pertenece a esta sede");
            }
        }

        Sale sale = new Sale();
        sale.setTenant(tenant);
        sale.setBranch(branch);
        sale.setCustomer(customer);
        sale.setAppointment(appointment);
        sale.setCashRegister(cashRegister);
        sale.setFechaCreacion(tenantTimeService.now(tenant.getId()));

        List<SaleItem> items = new ArrayList<>();
        List<SaleItemResponse> itemResponses = new ArrayList<>();
        BigDecimal subtotalVenta = BigDecimal.ZERO;

        Long uniqueBarberUserIdFromItems = null;
        Long firstBarberUserIdFromItems = null;
        boolean multipleBarbersInItems = false;

        for (SaleItemRequest itemRequest : request.getItems()) {
            SaleItem item = new SaleItem();
            item.setSale(sale);

            int cantidad = itemRequest.getCantidad() != null ? itemRequest.getCantidad() : 1;
            item.setCantidad(cantidad);

            BigDecimal precioUnitario = itemRequest.getPrecioUnitario() != null
                    ? BigDecimal.valueOf(itemRequest.getPrecioUnitario()).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            if (itemRequest.getServiceId() != null && itemRequest.getProductId() != null) {
                throw new RuntimeException("Cada item debe tener solo serviceId o productId, no ambos");
            }

            Product selectedProduct = null;

            if (itemRequest.getServiceId() != null) {
                ServiceEntity service = serviceRepository.findById(itemRequest.getServiceId())
                        .orElseThrow(() -> new RuntimeException(
                                "Servicio no encontrado: " + itemRequest.getServiceId()
                        ));

                if (!service.getTenant().getId().equals(tenant.getId())) {
                    throw new RuntimeException("El servicio no pertenece al tenant");
                }

                item.setService(service);
                item.setTipoItem("SERVICE");
                item.setNombreItem(clean(service.getNombre()) != null ? clean(service.getNombre()) : "Servicio");

                if (precioUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                    precioUnitario = BigDecimal.valueOf(service.getPrecio())
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }

            if (itemRequest.getProductId() != null) {
                Product product = productRepository.findByIdAndTenant_Id(itemRequest.getProductId(), tenant.getId())
                        .orElseThrow(() -> new RuntimeException(
                                "Producto no encontrado: " + itemRequest.getProductId()
                        ));

                if (!Boolean.TRUE.equals(product.getActivo())) {
                    throw new RuntimeException("El producto está inactivo: " + product.getNombre());
                }

                int stockActual = product.getStockActual() == null ? 0 : product.getStockActual();
                boolean permiteVentaSinStock = Boolean.TRUE.equals(product.getPermiteVentaSinStock());

                if (!permiteVentaSinStock && stockActual < cantidad) {
                    throw new RuntimeException("Stock insuficiente para el producto: " + product.getNombre());
                }

                item.setProduct(product);
                item.setTipoItem("PRODUCT");
                item.setNombreItem(clean(product.getNombre()) != null ? clean(product.getNombre()) : "Producto");
                selectedProduct = product;

                if (precioUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                    precioUnitario = resolveProductSalePrice(product);
                }
            }

            AppUser barberUser = null;
            if (itemRequest.getBarberUserId() != null) {
                barberUser = userRepository.findById(itemRequest.getBarberUserId())
                        .orElseThrow(() -> new RuntimeException(
                                "Barbero no encontrado: " + itemRequest.getBarberUserId()
                        ));

                if (!barberUser.getTenant().getId().equals(tenant.getId())) {
                    throw new RuntimeException("El barbero no pertenece al tenant");
                }

                item.setBarberUser(barberUser);

                if (firstBarberUserIdFromItems == null) {
                    firstBarberUserIdFromItems = barberUser.getId();
                }

                if (uniqueBarberUserIdFromItems == null) {
                    uniqueBarberUserIdFromItems = barberUser.getId();
                } else if (!Objects.equals(uniqueBarberUserIdFromItems, barberUser.getId())) {
                    multipleBarbersInItems = true;
                }
            }

            BigDecimal subtotalItem = precioUnitario
                    .multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.HALF_UP);

            item.setPrecioUnitario(precioUnitario);
            item.setSubtotal(subtotalItem);

            if (item.getService() != null) {
                item.setCostoUnitario(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                item.setGanancia(subtotalItem);
            }

            if (selectedProduct != null) {
                BigDecimal costoUnitario = safe(selectedProduct.getPrecioCompra()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal ganancia = subtotalItem.subtract(
                        costoUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP)
                ).setScale(2, RoundingMode.HALF_UP);

                item.setCostoUnitario(costoUnitario);
                item.setGanancia(ganancia);
                if (item.getBarberUser() != null) {
                    BigDecimal commission = safe(selectedProduct.getBarberCommissionAmount())
                            .multiply(BigDecimal.valueOf(cantidad))
                            .setScale(2, RoundingMode.HALF_UP);
                    item.setProductCommissionAmount(commission);
                } else {
                    item.setProductCommissionAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                }
            }

            items.add(item);
            subtotalVenta = subtotalVenta.add(subtotalItem);

            itemResponses.add(
                    SaleItemResponse.builder()
                            .id(null)
                            .serviceId(item.getService() != null ? item.getService().getId() : null)
                            .serviceNombre(item.getService() != null ? item.getService().getNombre() : null)
                            .serviceName(item.getService() != null ? item.getService().getNombre() : null)
                            .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                            .productName(item.getProduct() != null ? item.getProduct().getNombre() : null)
                            .barberUserId(barberUser != null ? barberUser.getId() : null)
                            .barberUserName(barberUser != null ? barberUser.getNombre() : null)
                            .cantidad(cantidad)
                            .precioUnitario(precioUnitario)
                            .subtotal(subtotalItem)
                            .productCommissionAmount(safe(item.getProductCommissionAmount()))
                            .build()
            );
        }

        boolean hasHaircutService = items.stream()
                .map(SaleItem::getService)
                .filter(Objects::nonNull)
                .anyMatch(this::isHaircutService);

        boolean hasCutData =
                hasText(request.getCutType()) ||
                        hasText(request.getCutDetail()) ||
                        hasText(request.getCutObservations());

        if (hasHaircutService && !hasCutData) {
            throw new RuntimeException("Debes registrar el corte realizado.");
        }

        if (user == null && uniqueBarberUserIdFromItems != null && !multipleBarbersInItems) {
            user = userRepository.findById(uniqueBarberUserIdFromItems)
                    .orElseThrow(() -> new RuntimeException("Barbero principal no encontrado"));
        }

        if (user != null && uniqueBarberUserIdFromItems != null && !multipleBarbersInItems) {
            if (!Objects.equals(user.getId(), uniqueBarberUserIdFromItems)) {
                user = userRepository.findById(uniqueBarberUserIdFromItems)
                        .orElseThrow(() -> new RuntimeException("Barbero principal no encontrado"));
            }
        }

        sale.setUser(user);

        BigDecimal discount = safe(request.getDiscount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tipAmount = safe(request.getTipAmount()).setScale(2, RoundingMode.HALF_UP);
        if (tipAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("La propina no puede ser negativa");
        }

        BigDecimal total = subtotalVenta.add(tipAmount).subtract(discount).setScale(2, RoundingMode.HALF_UP);

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        AppUser tipBarber = resolveTipBarber(tenant, request.getTipBarberUserId(), firstBarberUserIdFromItems, tipAmount);

        List<SalePayment> payments = buildPayments(request, total);
        String metodoPago = resolveMainPaymentMethod(request.getMetodoPago(), payments, total);
        BigDecimal cashReceived = resolveCashReceived(request, payments, metodoPago, total);
        BigDecimal changeAmount = resolveChangeAmount(metodoPago, cashReceived, total);

        sale.setMetodoPago(metodoPago);
        sale.setSubtotal(subtotalVenta.setScale(2, RoundingMode.HALF_UP));
        sale.setDiscount(discount);
        sale.setTipAmount(tipAmount);
        sale.setTipBarberUser(tipBarber);
        sale.setTotal(total);
        sale.setCashReceived(cashReceived);
        sale.setChangeAmount(changeAmount);
        sale.setItems(items);
        for (SalePayment payment : payments) {
            sale.addPayment(payment);
        }

        Sale savedSale = saleRepository.save(sale);

        registerProductStockMovements(savedSale, tenant, branch, user);

        customerCutHistoryService.registerFromSale(
                savedSale,
                clean(request.getCutType()),
                clean(request.getCutDetail()),
                clean(request.getCutObservations())
        );

        for (int i = 0; i < savedSale.getItems().size(); i++) {
            SaleItem savedItem = savedSale.getItems().get(i);
            SaleItemResponse old = itemResponses.get(i);

            itemResponses.set(i,
                    SaleItemResponse.builder()
                            .id(savedItem.getId())
                            .saleItemId(savedItem.getId())
                            .serviceId(old.getServiceId())
                            .serviceNombre(old.getServiceNombre())
                            .serviceName(old.getServiceName())
                            .productId(old.getProductId())
                            .productName(old.getProductName())
                            .barberUserId(old.getBarberUserId())
                            .barberUserName(old.getBarberUserName())
                            .cantidad(old.getCantidad())
                            .precioUnitario(old.getPrecioUnitario())
                            .subtotal(old.getSubtotal())
                            .productCommissionAmount(old.getProductCommissionAmount())
                            .build()
            );
        }

        int puntosGanados = 0;
        int puntosDisponibles = 0;

        if (customer != null && total.compareTo(BigDecimal.ZERO) > 0) {
            puntosGanados = calcularPuntos(total.doubleValue());

            loyaltyService.grantSalePoints(
                    tenant,
                    customer,
                    user,
                    savedSale,
                    total.doubleValue()
            );

            LoyaltyAccount updated = loyaltyAccountRepository
                    .findByTenant_IdAndCustomer_Id(tenant.getId(), customer.getId())
                    .orElse(null);

            puntosDisponibles = updated != null && updated.getPuntosDisponibles() != null
                    ? updated.getPuntosDisponibles()
                    : 0;
        }

        notificationService.notifyPointsEarned(customer, puntosGanados, savedSale.getId());

        return SaleResponse.builder()
                .saleId(savedSale.getId())
                .cashRegisterId(savedSale.getCashRegister() != null ? savedSale.getCashRegister().getId() : null)
                .tenantId(savedSale.getTenant().getId())
                .branchId(savedSale.getBranch().getId())
                .customerId(savedSale.getCustomer() != null ? savedSale.getCustomer().getId() : null)
                .userId(savedSale.getUser() != null ? savedSale.getUser().getId() : null)
                .appointmentId(savedSale.getAppointment() != null ? savedSale.getAppointment().getId() : null)
                .metodoPago(savedSale.getMetodoPago())
                .subtotal(savedSale.getSubtotal())
                .discount(savedSale.getDiscount())
                .tipAmount(safe(savedSale.getTipAmount()))
                .tipBarberUserId(savedSale.getTipBarberUser() != null ? savedSale.getTipBarberUser().getId() : null)
                .tipBarberUserName(savedSale.getTipBarberUser() != null ? savedSale.getTipBarberUser().getNombre() : null)
                .total(savedSale.getTotal())
                .cashReceived(savedSale.getCashReceived())
                .changeAmount(savedSale.getChangeAmount())
                .fechaCreacion(savedSale.getFechaCreacion())
                .puntosGanados(puntosGanados)
                .puntosDisponibles(puntosDisponibles)
                .items(itemResponses)
                .payments(mapPaymentResponses(savedSale))
                .build();
    }

    private List<SalePayment> buildPayments(CreateSaleRequest request, BigDecimal total) {
        List<SalePaymentRequest> rawPayments = request.getPayments();
        List<SalePayment> result = new ArrayList<>();

        if (rawPayments != null && !rawPayments.isEmpty()) {
            for (SalePaymentRequest paymentRequest : rawPayments) {
                if (paymentRequest == null) continue;
                BigDecimal amount = safe(paymentRequest.getAmount()).setScale(2, RoundingMode.HALF_UP);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;

                SalePayment payment = SalePayment.builder()
                        .method(normalizarMetodoPago(paymentRequest.getMethod()))
                        .amount(amount)
                        .build();
                result.add(payment);
            }
        }

        if (result.isEmpty()) {
            String method = normalizarMetodoPago(request.getMetodoPago());
            if ("FREE".equals(method) || total.compareTo(BigDecimal.ZERO) == 0) {
                return result;
            }
            result.add(SalePayment.builder()
                    .method(method)
                    .amount(total)
                    .build());
        }

        BigDecimal paid = result.stream()
                .map(SalePayment::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (paid.compareTo(total) != 0) {
            throw new RuntimeException("La suma de pagos debe ser igual al total de la venta. Total: " + total + ", pagado: " + paid);
        }

        return result;
    }

    private String resolveMainPaymentMethod(String requestedMethod, List<SalePayment> payments, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return "FREE";
        }
        if (payments == null || payments.isEmpty()) {
            return normalizarMetodoPago(requestedMethod);
        }
        long distinct = payments.stream()
                .map(p -> normalizarMetodoPago(p.getMethod()))
                .distinct()
                .count();
        if (distinct > 1) {
            return "MIXED";
        }
        return normalizarMetodoPago(payments.get(0).getMethod());
    }

    private BigDecimal resolveCashReceived(CreateSaleRequest request, List<SalePayment> payments, String metodoPago, BigDecimal total) {
        BigDecimal cashPart = payments == null ? BigDecimal.ZERO : payments.stream()
                .filter(p -> "EFECTIVO".equals(normalizarMetodoPago(p.getMethod())))
                .map(SalePayment::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if ("EFECTIVO".equals(metodoPago)) {
            BigDecimal received = safe(request.getCashReceived()).setScale(2, RoundingMode.HALF_UP);
            if (received.compareTo(BigDecimal.ZERO) <= 0) {
                received = total;
            }
            if (received.compareTo(total) < 0) {
                throw new RuntimeException("El monto recibido no puede ser menor al total");
            }
            return received;
        }

        if (cashPart.compareTo(BigDecimal.ZERO) > 0) {
            return cashPart;
        }

        if ("FREE".equals(metodoPago)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveChangeAmount(String metodoPago, BigDecimal cashReceived, BigDecimal total) {
        if (!"EFECTIVO".equals(metodoPago)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal change = safe(cashReceived).subtract(total).setScale(2, RoundingMode.HALF_UP);
        return change.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : change;
    }

    private AppUser resolveTipBarber(Tenant tenant, Long requestedTipBarberUserId, Long firstBarberUserIdFromItems, BigDecimal tipAmount) {
        if (tipAmount == null || tipAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Long barberId = requestedTipBarberUserId != null ? requestedTipBarberUserId : firstBarberUserIdFromItems;
        if (barberId == null) {
            throw new RuntimeException("Debes seleccionar un barbero para asignar la propina.");
        }

        AppUser barber = userRepository.findById(barberId)
                .orElseThrow(() -> new RuntimeException("Barbero de propina no encontrado"));

        if (!barber.getTenant().getId().equals(tenant.getId())) {
            throw new RuntimeException("El barbero de la propina no pertenece al tenant");
        }

        return barber;
    }

    private List<SalePaymentResponse> mapPaymentResponses(Sale sale) {
        if (sale.getPayments() == null) {
            return List.of();
        }
        return sale.getPayments().stream()
                .map(p -> SalePaymentResponse.builder()
                        .id(p.getId())
                        .method(p.getMethod())
                        .amount(safe(p.getAmount()))
                        .build())
                .toList();
    }

    private void registerProductStockMovements(Sale savedSale, Tenant tenant, Branch branch, AppUser user) {
        if (savedSale.getItems() == null || savedSale.getItems().isEmpty()) {
            return;
        }

        for (SaleItem savedItem : savedSale.getItems()) {
            if (savedItem.getProduct() == null) {
                continue;
            }

            Product product = savedItem.getProduct();
            int stockAnterior = product.getStockActual() == null ? 0 : product.getStockActual();
            int cantidadVendida = savedItem.getCantidad() == null ? 0 : savedItem.getCantidad();
            int stockNuevo = stockAnterior - cantidadVendida;

            product.setStockActual(stockNuevo);
            productRepository.save(product);

            StockMovement movement = StockMovement.builder()
                    .tenant(tenant)
                    .branch(branch)
                    .product(product)
                    .sale(savedSale)
                    .user(user)
                    .tipoMovimiento("VENTA")
                    .cantidad(cantidadVendida)
                    .stockAnterior(stockAnterior)
                    .stockNuevo(stockNuevo)
                    .costoUnitario(savedItem.getCostoUnitario())
                    .precioUnitario(savedItem.getPrecioUnitario())
                    .observacion("Salida automática por venta #" + savedSale.getId())
                    .fechaCreacion(savedSale.getFechaCreacion())
                    .build();

            stockMovementRepository.save(movement);
        }
    }

    private BigDecimal resolveProductSalePrice(Product product) {
        if (product.getPrecioVenta() != null && product.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0) {
            return product.getPrecioVenta().setScale(2, RoundingMode.HALF_UP);
        }

        if (product.getPrecio() != null && product.getPrecio() > 0) {
            return BigDecimal.valueOf(product.getPrecio()).setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private void validarRequest(CreateSaleRequest request) {
        if (request.getTenantId() == null) {
            throw new RuntimeException("tenantId es obligatorio");
        }

        if (request.getBranchId() == null) {
            throw new RuntimeException("branchId es obligatorio");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("La venta debe tener al menos un item");
        }

        for (SaleItemRequest item : request.getItems()) {
            if (item.getServiceId() == null && item.getProductId() == null) {
                throw new RuntimeException("Cada item debe tener serviceId o productId");
            }

            if (item.getServiceId() != null && item.getProductId() != null) {
                throw new RuntimeException("Cada item debe tener solo serviceId o productId");
            }

            if (item.getCantidad() == null || item.getCantidad() <= 0) {
                throw new RuntimeException("La cantidad debe ser mayor a 0");
            }
        }
    }

    private String normalizarMetodoPago(String metodoPago) {
        if (metodoPago == null) {
            return "EFECTIVO";
        }

        return switch (metodoPago.trim().toUpperCase()) {
            case "CASH", "EFECTIVO" -> "EFECTIVO";
            case "CARD", "TARJETA" -> "TARJETA";
            case "TRANSFER", "TRANSFERENCIA" -> "TRANSFER";
            case "YAPE" -> "YAPE";
            case "PLIN" -> "PLIN";
            case "MIXED", "MIXTO" -> "MIXED";
            case "FREE", "GRATIS", "CORTESIA", "CORTESÍA" -> "FREE";
            default -> metodoPago.trim().toUpperCase();
        };
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int calcularPuntos(double total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.floor(total * POINTS_PER_SOL);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean isHaircutService(ServiceEntity service) {
        if (service == null) {
            return false;
        }

        String categoria = service.getCategoria() == null
                ? ""
                : service.getCategoria().trim().toUpperCase();

        String nombre = service.getNombre() == null
                ? ""
                : service.getNombre().trim().toUpperCase();

        if (categoria.contains("BARBA")
                || nombre.contains("BARBA")
                || nombre.contains("AFEITADO")
                || nombre.contains("BIGOTE")
                || nombre.contains("CEJA")
                || nombre.contains("CEJAS")
                || nombre.contains("RAPADAS")
                || nombre.contains("RAPADA")
                || nombre.contains("TINTE")
                || nombre.contains("TINTES")
                || nombre.contains("ONDULACION")
                || nombre.contains("ONDULADO")
                || nombre.contains("PERFILADO")) {
            return false;
        }

        return "CORTE".equals(categoria)
                || "HAIRCUT".equals(categoria)
                || nombre.contains("CORTE")
                || nombre.contains("FADE")
                || nombre.contains("TAPER")
                || nombre.contains("DEGRADADO")
                || nombre.contains("CLASICO")
                || nombre.contains("CLÁSICO")
                || nombre.contains("BUZZ")
                || nombre.contains("CROP")
                || nombre.contains("MULLET");
    }
}
