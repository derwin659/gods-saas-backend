package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.AdjustProductStockRequest;
import com.gods.saas.domain.dto.request.SaveProductRequest;
import com.gods.saas.domain.dto.response.ProductResponse;
import com.gods.saas.domain.dto.response.StockMovementResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Product;
import com.gods.saas.domain.model.StockMovement;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.ProductRepository;
import com.gods.saas.domain.repository.StockMovementRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.OwnerProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Transactional
public class OwnerProductServiceImpl implements OwnerProductService {

    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TenantTimeService tenantTimeService;
    private final CloudinaryStorageService cloudinaryStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Long tenantId, Long branchId, Boolean activeOnly) {
        List<Product> products = Boolean.TRUE.equals(activeOnly)
                ? productRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                : productRepository.findByTenant_IdOrderByNombreAsc(tenantId);

        return products.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long tenantId, Long branchId, Long productId) {
        Product product = getProduct(tenantId, productId);
        return toResponse(product);
    }

    @Override
    public ProductResponse create(Long tenantId, Long branchId, Long userId, SaveProductRequest request) {
        validateRequest(request, true);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        Product product = new Product();
        product.setTenant(tenant);
        applyRequest(product, request);

        Product saved = productRepository.save(product);

        if (safeInt(saved.getStockActual()) > 0) {
            registerStockMovement(
                    tenantId,
                    branchId,
                    userId,
                    saved,
                    safeInt(saved.getStockActual()),
                    0,
                    safeInt(saved.getStockActual()),
                    "ENTRADA",
                    safe(saved.getPrecioCompra()),
                    null,
                    null,
                    null,
                    "Stock inicial del producto"
            );
        }

        return toResponse(saved);
    }

    @Override
    public ProductResponse update(Long tenantId, Long branchId, Long userId, Long productId, SaveProductRequest request) {
        validateRequest(request, false);

        Product product = getProduct(tenantId, productId);
        int previousStock = safeInt(product.getStockActual());

        applyRequest(product, request);

        Product saved = productRepository.save(product);

        if (request.getStockActual() != null && request.getStockActual() != previousStock) {
            int newStock = safeInt(saved.getStockActual());
            registerStockMovement(
                    tenantId,
                    branchId,
                    userId,
                    saved,
                    newStock - previousStock,
                    previousStock,
                    newStock,
                    "AJUSTE",
                    safe(saved.getPrecioCompra()),
                    null,
                    null,
                    null,
                    "Ajuste manual desde edición de producto"
            );
        }

        return toResponse(saved);
    }

    @Override
    public ProductResponse toggleActive(Long tenantId, Long branchId, Long userId, Long productId) {
        Product product = getProduct(tenantId, productId);
        product.setActivo(!Boolean.TRUE.equals(product.getActivo()));
        return toResponse(productRepository.save(product));
    }

    @Override
    public ProductResponse adjustStock(Long tenantId, Long branchId, Long userId, Long productId, AdjustProductStockRequest request) {
        if (request == null || request.getQuantityDelta() == null || request.getQuantityDelta() == 0) {
            throw new RuntimeException("Debes indicar una variación de stock válida");
        }

        Product product = getProduct(tenantId, productId);

        int stockAnterior = safeInt(product.getStockActual());
        int stockNuevo = stockAnterior + request.getQuantityDelta();

        if (stockNuevo < 0 && !Boolean.TRUE.equals(product.getPermiteVentaSinStock())) {
            throw new RuntimeException("El stock no puede quedar negativo");
        }

        product.setStockActual(stockNuevo);
        Product saved = productRepository.save(product);

        String tipoMovimiento = normalizeMovementType(request.getTipoMovimiento(), request.getQuantityDelta());
        BigDecimal costoUnitario = request.getCostoUnitario() != null
                ? request.getCostoUnitario()
                : safe(saved.getPrecioCompra());

        registerStockMovement(
                tenantId,
                branchId,
                userId,
                saved,
                request.getQuantityDelta(),
                stockAnterior,
                stockNuevo,
                tipoMovimiento,
                safe(costoUnitario),
                clean(request.getProveedor()),
                request.getFechaRecepcion(),
                clean(request.getNumeroComprobante()),
                clean(request.getObservacion()) != null
                        ? clean(request.getObservacion())
                        : defaultMovementObservation(tipoMovimiento, request.getQuantityDelta())
        );

        return toResponse(saved);
    }


    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponse> findStockMovements(Long tenantId, Long branchId, Long productId, int limit) {
        getProduct(tenantId, productId);

        int safeLimit = Math.min(Math.max(limit, 1), 50);

        return stockMovementRepository
                .findByTenant_IdAndBranch_IdAndProduct_IdOrderByFechaCreacionDesc(
                        tenantId,
                        branchId,
                        productId,
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(StockMovementResponse::fromEntity)
                .toList();
    }


    @Override
    public ProductResponse uploadImage(Long tenantId, Long branchId, Long userId, Long productId, MultipartFile file) {
        Product product = getProduct(tenantId, productId);

        if (product.getImagePublicId() != null && !product.getImagePublicId().isBlank()) {
            cloudinaryStorageService.deleteImage(product.getImagePublicId());
        }

        CloudinaryStorageService.UploadResult upload =
                cloudinaryStorageService.uploadProductImage(tenantId, productId, file);

        product.setImageUrl(upload.getSecureUrl());
        product.setImagePublicId(upload.getPublicId());

        return toResponse(productRepository.save(product));
    }

    private void applyRequest(Product product, SaveProductRequest request) {
        if (clean(request.getNombre()) != null) {
            product.setNombre(clean(request.getNombre()));
        }

        product.setSku(clean(request.getSku()));
        product.setDescripcion(clean(request.getDescripcion()));
        product.setCategoria(clean(request.getCategoria()));

        BigDecimal precioVenta = firstPositive(request.getPrecioVenta(), request.getPrecio(), toBigDecimal(product.getPrecio()));
        BigDecimal precioCompra = request.getPrecioCompra() != null ? request.getPrecioCompra() : safe(product.getPrecioCompra());

        product.setPrecioCompra(safe(precioCompra));
        product.setPrecioVenta(safe(precioVenta));
        product.setPrecio(safe(precioVenta).doubleValue());

        BigDecimal barberCommissionAmount = request.getBarberCommissionAmount() != null
                ? request.getBarberCommissionAmount()
                : safe(product.getBarberCommissionAmount());
        product.setBarberCommissionAmount(safe(barberCommissionAmount));

        if (request.getStockActual() != null) {
            product.setStockActual(Math.max(0, request.getStockActual()));
        } else if (product.getStockActual() == null) {
            product.setStockActual(0);
        }

        if (request.getStockMinimo() != null) {
            product.setStockMinimo(Math.max(0, request.getStockMinimo()));
        } else if (product.getStockMinimo() == null) {
            product.setStockMinimo(0);
        }

        if (request.getActivo() != null) {
            product.setActivo(request.getActivo());
        } else if (product.getActivo() == null) {
            product.setActivo(true);
        }

        if (request.getPermiteVentaSinStock() != null) {
            product.setPermiteVentaSinStock(request.getPermiteVentaSinStock());
        } else if (product.getPermiteVentaSinStock() == null) {
            product.setPermiteVentaSinStock(false);
        }
    }

    private void validateRequest(SaveProductRequest request, boolean creating) {
        if (request == null) {
            throw new RuntimeException("Request inválido");
        }

        if (creating && clean(request.getNombre()) == null) {
            throw new RuntimeException("El nombre del producto es obligatorio");
        }

        BigDecimal precioVenta = firstPositive(request.getPrecioVenta(), request.getPrecio());
        if (creating && precioVenta.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El precio de venta debe ser mayor a 0");
        }

        if (request.getPrecioCompra() != null && request.getPrecioCompra().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("El precio de compra no puede ser negativo");
        }

        if (request.getBarberCommissionAmount() != null
                && request.getBarberCommissionAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("La comisión del barbero no puede ser negativa");
        }

        if (request.getStockActual() != null && request.getStockActual() < 0) {
            throw new RuntimeException("El stock actual no puede ser negativo");
        }

        if (request.getStockMinimo() != null && request.getStockMinimo() < 0) {
            throw new RuntimeException("El stock mínimo no puede ser negativo");
        }
    }

    private Product getProduct(Long tenantId, Long productId) {
        return productRepository.findByIdAndTenant_Id(productId, tenantId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    private ProductResponse toResponse(Product product) {
        int stockActual = safeInt(product.getStockActual());
        int stockMinimo = safeInt(product.getStockMinimo());

        return ProductResponse.builder()
                .id(product.getId())
                .nombre(product.getNombre())
                .sku(product.getSku())
                .descripcion(product.getDescripcion())
                .precioCompra(safe(product.getPrecioCompra()))
                .precioVenta(resolvePrecioVenta(product))
                .precio(product.getPrecio())
                .barberCommissionAmount(safe(product.getBarberCommissionAmount()))
                .stockActual(stockActual)
                .stockMinimo(stockMinimo)
                .categoria(product.getCategoria())
                .imageUrl(product.getImageUrl())
                .imagePublicId(product.getImagePublicId())
                .activo(Boolean.TRUE.equals(product.getActivo()))
                .permiteVentaSinStock(Boolean.TRUE.equals(product.getPermiteVentaSinStock()))
                .stockBajo(stockActual <= stockMinimo)
                .build();
    }

    private BigDecimal resolvePrecioVenta(Product product) {
        if (product.getPrecioVenta() != null && product.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0) {
            return product.getPrecioVenta();
        }
        if (product.getPrecio() != null) {
            return BigDecimal.valueOf(product.getPrecio());
        }
        return BigDecimal.ZERO;
    }

    private void registerStockMovement(
            Long tenantId,
            Long branchId,
            Long userId,
            Product product,
            Integer quantityDelta,
            Integer stockAnterior,
            Integer stockNuevo,
            String type,
            BigDecimal costoUnitario,
            String proveedor,
            java.time.LocalDate fechaRecepcion,
            String numeroComprobante,
            String observacion
    ) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sede no encontrada"));

        if (!branch.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("La sede no pertenece al tenant");
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!user.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El usuario no pertenece al tenant");
        }

        StockMovement movement = StockMovement.builder()
                .tenant(product.getTenant())
                .branch(branch)
                .product(product)
                .user(user)
                .tipoMovimiento(type)
                .cantidad(Math.abs(quantityDelta))
                .stockAnterior(stockAnterior)
                .stockNuevo(stockNuevo)
                .costoUnitario(safe(costoUnitario))
                .precioUnitario(resolvePrecioVenta(product))
                .proveedor(proveedor)
                .fechaRecepcion(fechaRecepcion)
                .numeroComprobante(numeroComprobante)
                .observacion(observacion)
                .fechaCreacion(tenantTimeService.now(tenantId))
                .build();

        stockMovementRepository.save(movement);
    }

    private String normalizeMovementType(String rawType, Integer quantityDelta) {
        String type = clean(rawType);

        if (type == null) {
            return quantityDelta != null && quantityDelta > 0 ? "ENTRADA" : "AJUSTE";
        }

        String normalized = type
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');

        return switch (normalized) {
            case "ENTRADA", "AJUSTE", "PERDIDA", "DEVOLUCION", "SALIDA_INTERNA", "VENTA" -> normalized;
            default -> quantityDelta != null && quantityDelta > 0 ? "ENTRADA" : "AJUSTE";
        };
    }

    private String defaultMovementObservation(String tipoMovimiento, Integer quantityDelta) {
        String type = tipoMovimiento == null ? "AJUSTE" : tipoMovimiento;

        return switch (type) {
            case "ENTRADA" -> "Recepción o entrada manual de stock";
            case "PERDIDA" -> "Salida por pérdida o merma";
            case "DEVOLUCION" -> "Devolución de stock";
            case "SALIDA_INTERNA" -> "Salida interna de inventario";
            case "VENTA" -> "Salida por venta";
            default -> quantityDelta != null && quantityDelta > 0
                    ? "Entrada manual de stock"
                    : "Ajuste manual de stock";
        };
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) return BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }
}
