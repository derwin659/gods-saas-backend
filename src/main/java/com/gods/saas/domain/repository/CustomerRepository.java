package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhonePendiente(String phonePendiente);
    Optional<Customer> findByPhonePendienteAndTenantId(String phonePendiente, Long tenantId);
    Optional<Customer> findByTelefonoAndTenantId(String phone, Long tenantId);

    Optional<Customer> findByTenantIdAndTelefono(Long tenantId, String telefono);

    Optional<Customer> findByTenant_IdAndTelefono(Long tenantId, String telefono);
    boolean existsByTenant_IdAndTelefono(Long tenantId, String telefono);

    Optional<Customer> findByEmailAndTenantId(String email, Long tenantId);
    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Customer> findByTelefono(String telefono);
    Optional<Customer> findByTenantIdAndId(Long tenantId, Long id);
    boolean existsByTelefonoAndTenantId(String telefono, Long tenantId);
    Optional<Customer> findByTenant_IdAndId(Long tenantId, Long id);
    boolean existsByEmailAndTenantId(String email, Long tenantId);

}
