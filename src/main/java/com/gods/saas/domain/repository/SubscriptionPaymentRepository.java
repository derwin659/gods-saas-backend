package com.gods.saas.domain.repository;

import com.gods.saas.domain.dto.response.SuperAdminPaymentResponse;
import com.gods.saas.domain.model.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<SubscriptionPayment> findTopByTenantIdAndStatusOrderByCreatedAtDesc(Long tenantId, String status);


    // Pagos pendientes
    List<SubscriptionPayment> findByStatusOrderByCreatedAtDesc(String status);

    // Todos los pagos ordenados
    List<SubscriptionPayment> findAllByOrderByCreatedAtDesc();

    // Conteo para dashboard
    long countByStatus(String status);

}
