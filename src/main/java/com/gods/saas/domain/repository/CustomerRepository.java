package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.Customer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByTenant_IdAndNombresContainingIgnoreCaseOrderByNombresAsc(
            Long tenantId, String nombres, Pageable pageable
    );

    List<Customer> findByTenant_IdAndTelefonoContainingOrderByNombresAsc(
            Long tenantId, String telefono, Pageable pageable
    );

    Optional<Customer> findByPhonePendiente(String phonePendiente);
    Optional<Customer> findByPhonePendienteAndTenantId(String phonePendiente, Long tenantId);
    Optional<Customer> findByTelefonoAndTenantId(String phone, Long tenantId);
    Optional<Customer> findByTenantIdAndTelefono(Long tenantId, String telefono);
    Optional<Customer> findByEmailAndTenantId(String email, Long tenantId);
    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Customer> findByTelefono(String telefono);
    Optional<Customer> findByTenantIdAndId(Long tenantId, Long id);
    boolean existsByTelefonoAndTenantId(String telefono, Long tenantId);
    Optional<Customer> findByTenant_IdAndId(Long tenantId, Long id);
    boolean existsByEmailAndTenantId(String email, Long tenantId);


    @Query("""
    select c
    from Customer c
    join fetch c.tenant t
    left join fetch t.settings
    where t.id = :tenantId
      and c.telefono = :telefono
""")
    Optional<Customer> findByTenantIdAndTelefonoWithTenant(
            @Param("tenantId") Long tenantId,
            @Param("telefono") String telefono
    );

    @Query("""
    select c
    from Customer c
    join fetch c.tenant t
    left join fetch t.settings
    where c.id = :customerId
      and t.id = :tenantId
""")
    Optional<Customer> findByIdAndTenantIdWithTenant(
            @Param("customerId") Long customerId,
            @Param("tenantId") Long tenantId
    );



    Optional<Customer> findByIdAndTenant_Id(Long id, Long tenantId);

    Optional<Customer> findByTenant_IdAndTelefono(Long tenantId, String telefono);

    boolean existsByTenant_IdAndTelefono(Long tenantId, String telefono);

    @Query("""
        SELECT c
        FROM Customer c
        WHERE c.tenant.id = :tenantId
          AND (
                LOWER(COALESCE(c.nombres, '')) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(COALESCE(c.apellidos, '')) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(CONCAT(COALESCE(c.nombres, ''), ' ', COALESCE(c.apellidos, ''))) LIKE LOWER(CONCAT('%', :q, '%'))
             OR COALESCE(c.telefono, '') LIKE CONCAT('%', :q, '%')
          )
        ORDER BY c.nombres ASC, c.apellidos ASC
    """)
    List<Customer> searchByNameOrPhone(Long tenantId, String q, Pageable pageable);

    @Query("""
    select count(c)
    from Customer c
    where c.tenant.id = :tenantId
""")
    Integer countCustomers(@Param("tenantId") Long tenantId);



}