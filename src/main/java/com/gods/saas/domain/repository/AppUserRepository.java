package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    List<AppUser> findByTenant_IdAndBranch_IdAndRolAndActivoTrueOrderByNombreAsc(
            Long tenantId,
            Long branchId,
            String rol
    );

    List<AppUser> findByTenant_IdAndRolAndActivoTrueOrderByNombreAsc(Long tenantId, String rol);
    List<AppUser> findByTenant_IdAndRolAndActivoTrue(Long tenantId, String rol);

    Optional<AppUser> findByIdAndTenant_Id(Long userId, Long tenantId);

    Optional<AppUser> findByEmailAndTenantId(String email, Long tenantId);

    Optional<AppUser> findByEmail(String email);

    List<AppUser> findByTenantId(Long tenantId);

    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByEmailAndTenantId(String email, Long tenantId);

    boolean existsByEmail(String email);

    int countByTenantId(Long tenantId);

    List<AppUser> findByTenant_IdAndRol(Long tenantId, String rol);

    List<AppUser> findByTenant_IdAndBranch_IdAndRol(Long tenantId, Long branchId, String rol);

    Optional<AppUser> findByEmailAndTenant_Id(String email, Long tenantId);

    boolean existsByEmailAndTenant_Id(String email, Long tenantId);

    boolean existsByEmailAndTenant_IdAndIdNot(String email, Long tenantId, Long userId);

    long countByTenantIdAndRolIgnoreCaseAndActivoTrue(Long tenantId, String rol);

    @Query("""
       select count(u)
       from AppUser u
       where u.tenant.id = :tenantId
         and upper(u.rol) in :roles
         and u.activo = true
       """)
    long countActiveByTenantIdAndRoles(@Param("tenantId") Long tenantId,
                                       @Param("roles") List<String> roles);

    Optional<AppUser> findFirstByTenantIdAndRolOrderByIdAsc(Long id, String owner);


    @Query("""
    select u
    from AppUser u
    left join fetch u.branch b
    where u.tenant.id = :tenantId
      and u.rol = :rol
""")
    List<AppUser> findByTenantIdAndRolWithBranch(
            @Param("tenantId") Long tenantId,
            @Param("rol") String rol
    );

    @Query("""
    select u
    from AppUser u
    left join fetch u.branch b
    where u.tenant.id = :tenantId
      and b.id = :branchId
      and u.rol = :rol
""")
    List<AppUser> findByTenantIdAndBranchIdAndRolWithBranch(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("rol") String rol
    );


    @Query("""
    select u
    from AppUser u
    left join fetch u.tenant t
    where u.id = :userId
""")
    Optional<AppUser> findByIdWithTenant(@Param("userId") Long userId);
}