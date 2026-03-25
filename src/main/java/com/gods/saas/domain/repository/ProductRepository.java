package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository  extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndTenant_Id(Long id, Long tenantId);
}
