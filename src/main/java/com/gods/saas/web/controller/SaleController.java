package com.gods.saas.web.controller;


import com.gods.saas.domain.dto.request.CreateSaleRequest;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.service.impl.impl.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    public SaleResponse crearVenta(@RequestBody CreateSaleRequest request) {
        return saleService.crearVenta(request);
    }

}
