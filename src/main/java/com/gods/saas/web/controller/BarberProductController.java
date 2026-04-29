package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.ProductResponse;
import com.gods.saas.domain.model.Product;
import com.gods.saas.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/barber/products")
@RequiredArgsConstructor
public class BarberProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public List<ProductResponse> findActiveProducts(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long branchId
    ) {
        return productRepository
                .findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ProductResponse toResponse(Product product) {
        int stockActual = product.getStockActual() == null ? 0 : product.getStockActual();
        int stockMinimo = product.getStockMinimo() == null ? 0 : product.getStockMinimo();

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

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
