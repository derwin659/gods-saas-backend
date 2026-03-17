package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateSaleRequest;
import com.gods.saas.domain.dto.request.SaleItemRequest;
import com.gods.saas.domain.dto.response.SaleItemResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.Product;
import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.model.SaleItem;
import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.ProductRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.LoyaltyService;
import com.gods.saas.service.impl.impl.SaleService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

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
    private OffsetDateTime fechaCreacion;

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
        }

        String metodoPago = normalizarMetodoPago(request.getMetodoPago());

        Sale sale = new Sale();
        sale.setTenant(tenant);
        sale.setBranch(branch);
        sale.setCustomer(customer);
        sale.setUser(user);
        sale.setAppointment(appointment);
        sale.setMetodoPago(metodoPago);
        sale.setFechaCreacion(LocalDateTime.now(ZoneOffset.UTC));

        List<SaleItem> items = new ArrayList<>();
        List<SaleItemResponse> itemResponses = new ArrayList<>();
        BigDecimal subtotalVenta = BigDecimal.ZERO;

        for (SaleItemRequest itemRequest : request.getItems()) {
            SaleItem item = new SaleItem();
            item.setSale(sale);

            int cantidad = itemRequest.getCantidad() != null ? itemRequest.getCantidad() : 1;
            item.setCantidad(cantidad);

            BigDecimal precioUnitario = itemRequest.getPrecioUnitario() != null
                    ? BigDecimal.valueOf(itemRequest.getPrecioUnitario()).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            if (itemRequest.getServiceId() != null) {
                ServiceEntity service = serviceRepository.findById(itemRequest.getServiceId())
                        .orElseThrow(() -> new RuntimeException(
                                "Servicio no encontrado: " + itemRequest.getServiceId()
                        ));

                if (!service.getTenant().getId().equals(tenant.getId())) {
                    throw new RuntimeException("El servicio no pertenece al tenant");
                }

                item.setService(service);

                if (precioUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                    precioUnitario = BigDecimal.valueOf(service.getPrecio())
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }

            if (itemRequest.getProductId() != null) {
                Product product = productRepository.findById(itemRequest.getProductId())
                        .orElseThrow(() -> new RuntimeException(
                                "Producto no encontrado: " + itemRequest.getProductId()
                        ));

                if (!product.getTenant().getId().equals(tenant.getId())) {
                    throw new RuntimeException("El producto no pertenece al tenant");
                }

                item.setProduct(product);

                if (precioUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                    precioUnitario = BigDecimal.valueOf(product.getPrecio())
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }

            BigDecimal subtotalItem = precioUnitario
                    .multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.HALF_UP);

            item.setPrecioUnitario(precioUnitario.doubleValue());
            item.setSubtotal(subtotalItem.doubleValue());

            items.add(item);
            subtotalVenta = subtotalVenta.add(subtotalItem);

            itemResponses.add(
                    SaleItemResponse.builder()
                            .id(null)
                            .serviceId(item.getService() != null ? item.getService().getId() : null)
                            .serviceNombre(item.getService() != null ? item.getService().getNombre() : null)
                            .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                            .productNombre(item.getProduct() != null ? item.getProduct().getNombre() : null)
                            .cantidad(cantidad)
                            .precioUnitario(precioUnitario.doubleValue())
                            .subtotal(subtotalItem.doubleValue())
                            .build()
            );
        }

        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subtotalVenta.subtract(discount).setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashReceived;
        BigDecimal changeAmount;

        if ("EFECTIVO".equals(metodoPago)) {
            if (request.getCashReceived() == null) {
                throw new RuntimeException("cashReceived es obligatorio cuando el método de pago es EFECTIVO");
            }

            cashReceived = BigDecimal.valueOf(request.getCashReceived())
                    .setScale(2, RoundingMode.HALF_UP);

            if (cashReceived.compareTo(total) < 0) {
                throw new RuntimeException("El monto recibido no puede ser menor al total de la venta");
            }

            changeAmount = cashReceived.subtract(total).setScale(2, RoundingMode.HALF_UP);
        } else {
            cashReceived = total;
            changeAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        sale.setSubtotal(subtotalVenta.setScale(2, RoundingMode.HALF_UP));
        sale.setDiscount(discount);
        sale.setTotal(total);
        sale.setCashReceived(cashReceived);
        sale.setChangeAmount(changeAmount);
        sale.setItems(items);

        Sale savedSale = saleRepository.save(sale);

        for (int i = 0; i < savedSale.getItems().size(); i++) {
            SaleItem savedItem = savedSale.getItems().get(i);
            SaleItemResponse old = itemResponses.get(i);

            itemResponses.set(i,
                    SaleItemResponse.builder()
                            .id(savedItem.getId())
                            .serviceId(old.getServiceId())
                            .serviceNombre(old.getServiceNombre())
                            .productId(old.getProductId())
                            .productNombre(old.getProductNombre())
                            .cantidad(old.getCantidad())
                            .precioUnitario(old.getPrecioUnitario())
                            .subtotal(old.getSubtotal())
                            .build()
            );
        }

        int puntosGanados = 0;
        int puntosDisponibles = 0;

        if (customer != null) {
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

        return SaleResponse.builder()
                .saleId(savedSale.getId())
                .tenantId(savedSale.getTenant().getId())
                .branchId(savedSale.getBranch().getId())
                .customerId(savedSale.getCustomer() != null ? savedSale.getCustomer().getId() : null)
                .userId(savedSale.getUser() != null ? savedSale.getUser().getId() : null)
                .appointmentId(savedSale.getAppointment() != null ? savedSale.getAppointment().getId() : null)
                .metodoPago(savedSale.getMetodoPago())
                .total(savedSale.getTotal().doubleValue())
                .fechaCreacion(savedSale.getFechaCreacion())
                .puntosGanados(puntosGanados)
                .puntosDisponibles(puntosDisponibles)
                .items(itemResponses)
                .build();
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
            case "TRANSFER", "TRANSFERENCIA", "YAPE", "PLIN", "YAPE_PLIN" -> "YAPE";
            default -> metodoPago.trim().toUpperCase();
        };
    }

    private int calcularPuntos(double total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.floor(total * POINTS_PER_SOL);
    }
}