package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.AdjustProductStockRequest;
import com.gods.saas.domain.dto.request.SaveProductRequest;
import com.gods.saas.domain.dto.response.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OwnerProductService {

    List<ProductResponse> findAll(Long tenantId, Long branchId, Boolean activeOnly);

    ProductResponse findById(Long tenantId, Long branchId, Long productId);

    ProductResponse create(Long tenantId, Long branchId, Long userId, SaveProductRequest request);

    ProductResponse update(Long tenantId, Long branchId, Long userId, Long productId, SaveProductRequest request);

    ProductResponse toggleActive(Long tenantId, Long branchId, Long userId, Long productId);

    ProductResponse adjustStock(Long tenantId, Long branchId, Long userId, Long productId, AdjustProductStockRequest request);

    ProductResponse uploadImage(Long tenantId, Long branchId, Long userId, Long productId, MultipartFile file);
}

