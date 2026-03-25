package com.gods.saas.domain.repository;
import com.gods.saas.domain.dto.TenantDto;
import com.gods.saas.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {


    long countByActiveTrue();
    List<Tenant> findTop5ByOrderByFechaCreacionDesc();
    List<Tenant> findByActiveTrue();

    Optional<Tenant> findByCodigoIgnoreCaseAndActiveTrue(String codigo);

    boolean existsByCodigo(String code);
}