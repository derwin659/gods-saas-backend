package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateCashSaleItemRequest;
import com.gods.saas.domain.dto.request.CreateCashSaleRequest;
import com.gods.saas.domain.dto.response.SaleItemResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.enums.CashRegisterStatus;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.CashRegister;
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
import com.gods.saas.domain.repository.CashRegisterRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.ProductRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.CashSaleService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CashSaleServiceImpl implements CashSaleService {

    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceRepository serviceRepository;
    private final ProductRepository productRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;

    @Override
    public SaleResponse createCashSale(Long tenantId, Long branchId, Long userId, CreateCashSaleRequest request) {

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos un item.");
        }

        CashRegister cashRegister = cashRegisterRepository
                .findByTenant_IdAndBranch_IdAndStatus(tenantId, branchId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("No hay una caja abierta en esta sede."));

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Sede no encontrada"));

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findByIdAndTenant_Id(request.getCustomerId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"));
        }

        Appointment appointment = null;
        if (request.getAppointmentId() != null) {
            appointment = appointmentRepository.findByIdAndTenant_Id(request.getAppointmentId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Cita no encontrada"));

            if (appointment.getBranch() == null || !appointment.getBranch().getId().equals(branchId)) {
                throw new IllegalStateException("La cita no pertenece a esta sede.");
            }

            String estadoActual = appointment.getEstado() == null ? "" : appointment.getEstado().trim().toUpperCase();
            if (estadoActual.equals("COMPLETADO")
                    || estadoActual.equals("ATENDIDO")
                    || estadoActual.equals("FINALIZADO")
                    || estadoActual.equals("CANCELADO")) {
                throw new IllegalStateException("La cita ya no puede ser atendida.");
            }
        }

        Sale sale = Sale.builder()
                .tenant(tenant)
                .branch(branch)
                .customer(customer)
                .user(user)
                .cashRegister(cashRegister)
                .metodoPago(normalizeMethod(request.getMetodoPago()))
                .fechaCreacion(LocalDateTime.now())
                .appointment(appointment)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<SaleItem> saleItems = new ArrayList<>();

        for (CreateCashSaleItemRequest itemRequest : request.getItems()) {
            SaleItem item = buildSaleItem(tenantId, itemRequest, sale);
            subtotal = subtotal.add(safe(item.getSubtotal()));
            saleItems.add(item);
        }

        BigDecimal discount = safe(request.getDiscount());
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("El descuento no puede ser mayor al subtotal.");
        }

        BigDecimal total = subtotal.subtract(discount);

        BigDecimal cashReceived = safe(request.getCashReceived());
        BigDecimal changeAmount = BigDecimal.ZERO;

        if ("CASH".equalsIgnoreCase(sale.getMetodoPago())) {
            if (cashReceived.compareTo(total) < 0) {
                throw new IllegalArgumentException("El monto recibido no puede ser menor al total.");
            }
            changeAmount = cashReceived.subtract(total);
        }

        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setTotal(total);
        sale.setCashReceived(cashReceived);
        sale.setChangeAmount(changeAmount);
        sale.setItems(saleItems);

        saleRepository.save(sale);

        if (appointment != null) {
            appointment.setEstado("COMPLETADO");
            appointmentRepository.save(appointment);
        }

        int puntosGanados = 0;
        if (customer != null) {
            puntosGanados = addPointsToCustomer(customer, total);
        }

        return mapResponse(sale, puntosGanados);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getTodaySales(Long tenantId, Long branchId) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        return saleRepository
                .findByTenant_IdAndBranch_IdAndFechaCreacionBetweenOrderByFechaCreacionDesc(tenantId, branchId, from, to)
                .stream()
                .map(sale -> mapResponse(sale, 0))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByRange(Long tenantId, Long branchId, LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        return saleRepository
                .findByTenant_IdAndBranch_IdAndFechaCreacionBetweenOrderByFechaCreacionDesc(
                        tenantId, branchId, fromDateTime, toDateTime
                )
                .stream()
                .map(sale -> mapResponse(sale, 0))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getById(Long tenantId, Long saleId) {
        Sale sale = saleRepository.findByIdAndTenant_Id(saleId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada"));

        return mapResponse(sale, 0);
    }

    private SaleItem buildSaleItem(Long tenantId, CreateCashSaleItemRequest request, Sale sale) {
        boolean hasService = request.getServiceId() != null;
        boolean hasProduct = request.getProductId() != null;

        if (!hasService && !hasProduct) {
            throw new IllegalArgumentException("Cada item debe tener servicio o producto.");
        }

        if (hasService && hasProduct) {
            throw new IllegalArgumentException("Un item no puede tener servicio y producto a la vez.");
        }

        Integer cantidad = (request.getCantidad() == null || request.getCantidad() <= 0) ? 1 : request.getCantidad();

        ServiceEntity service = null;
        Product product = null;
        BigDecimal precioUnitario = request.getPrecioUnitario();

        if (hasService) {
            service = serviceRepository.findByIdAndTenant_Id(request.getServiceId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Servicio no encontrado"));

            if (precioUnitario == null) {
                precioUnitario = toBigDecimal(service.getPrecio());
            }
        }

        if (hasProduct) {
            product = productRepository.findByIdAndTenant_Id(request.getProductId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));

            if (precioUnitario == null) {
                precioUnitario = toBigDecimal(product.getPrecio());
            }
        }

        AppUser barberUser = null;
        if (request.getBarberUserId() != null) {
            barberUser = appUserRepository.findByIdAndTenant_Id(request.getBarberUserId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Barbero no encontrado"));
        }

        precioUnitario = safe(precioUnitario);
        BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));

        return SaleItem.builder()
                .sale(sale)
                .service(service)
                .product(product)
                .barberUser(barberUser)
                .cantidad(cantidad)
                .precioUnitario(precioUnitario)
                .subtotal(subtotal)
                .build();
    }

    private int addPointsToCustomer(Customer customer, BigDecimal total) {
        int puntosGanados = calculatePoints(total);

        if (puntosGanados <= 0) {
            return 0;
        }

        LoyaltyAccount loyaltyAccount = loyaltyAccountRepository
                .findByCustomer_Id(customer.getId())
                .orElse(null);

        if (loyaltyAccount == null) {
            return 0;
        }

        Integer puntosActuales = loyaltyAccount.getPuntosDisponibles() == null ? 0 : loyaltyAccount.getPuntosDisponibles();
        Integer puntosAcumulados = loyaltyAccount.getPuntosAcumulados() == null ? 0 : loyaltyAccount.getPuntosAcumulados();

        loyaltyAccount.setPuntosAcumulados(puntosActuales + puntosGanados);
        loyaltyAccount.setPuntosAcumulados(puntosAcumulados + puntosGanados);

        loyaltyAccountRepository.save(loyaltyAccount);

        return puntosGanados;
    }

    private int calculatePoints(BigDecimal total) {
        if (total == null) return 0;
        return total.divide(BigDecimal.TEN, 0, java.math.RoundingMode.DOWN).intValue();
    }

    private SaleResponse mapResponse(Sale sale, Integer puntosGanados) {
        return SaleResponse.builder()
                .saleId(sale.getId())
                .cashRegisterId(sale.getCashRegister() != null ? sale.getCashRegister().getId() : null)
                .customerId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                .customerName(sale.getCustomer() != null ? sale.getCustomer().getNombres() : null)
                .appointmentId(sale.getAppointment() != null ? sale.getAppointment().getId(): null)
                .metodoPago(sale.getMetodoPago())
                .subtotal(safe(sale.getSubtotal()))
                .discount(safe(sale.getDiscount()))
                .total(BigDecimal.valueOf(safe(sale.getTotal()).doubleValue()))
                .cashReceived(safe(sale.getCashReceived()))
                .changeAmount(safe(sale.getChangeAmount()))
                .fechaCreacion(sale.getFechaCreacion())
                .puntosGanados(puntosGanados == null ? 0 : puntosGanados)
                .items(
                        sale.getItems() == null
                                ? List.of()
                                : sale.getItems().stream().map(item ->
                                SaleItemResponse.builder()
                                        .saleItemId(item.getId())
                                        .serviceId(item.getService() != null ? item.getService().getId() : null)
                                        .serviceName(item.getService() != null ? item.getService().getNombre() : null)
                                        .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                                        .productName(item.getProduct() != null ? item.getProduct().getNombre() : null)
                                        .barberUserId(item.getBarberUser() != null ? item.getBarberUser().getId() : null)
                                        .barberUserName(item.getBarberUser() != null ? item.getBarberUser().getNombre() : null)
                                        .cantidad(item.getCantidad())
                                        .precioUnitario(safe(item.getPrecioUnitario()))
                                        .subtotal(safe(item.getSubtotal()))
                                        .build()
                        ).collect(Collectors.toList())
                )
                .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }



    private BigDecimal toBigDecimal(Number value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value.doubleValue());
    }

    private String normalizeMethod(String metodoPago) {
        if (metodoPago == null || metodoPago.isBlank()) {
            return "CASH";
        }
        return metodoPago.trim().toUpperCase();
    }
}
