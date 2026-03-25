package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.Subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface SuscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findTopByTenantIdOrderBySubIdDesc(Long tenantId);
    // Buscar suscripción por tenant
    Optional<Subscription> findByTenantId(Long tenantId);

    // Contadores para dashboard
    long countByEstado(String estado);

}
