package com.gods.saas.service.impl.impl;


import com.gods.saas.domain.dto.request.CreateSaleRequest;
import com.gods.saas.domain.dto.response.SaleResponse;

public interface SaleService {
    SaleResponse crearVenta(CreateSaleRequest request);
}

