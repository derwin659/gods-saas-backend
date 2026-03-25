package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.UserTenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTenantRoleRepository extends JpaRepository<UserTenantRole, Long> {

    List<UserTenantRole> findByUserId(Long userId);

    Optional<UserTenantRole> findByUserIdAndTenantId(Long userId, Long tenantId);

    List<UserTenantRole> findByTenantId(Long tenantId);

    List<UserTenantRole> findByUser_Id(Long userId);

    Optional<UserTenantRole> findByUser_IdAndTenant_Id(Long userId, Long tenantId);

    List<UserTenantRole> findByTenant_Id(Long tenantId);

    boolean existsByUser_IdAndTenant_IdAndRole(Long userId, Long tenantId, RoleType role);

    @Query("""
    select count(r)
    from UserTenantRole r
    where r.tenant.id = :tenantId
      and (:branchId is null or r.branch.id = :branchId)
      and r.role = 'BARBER'
      and r.user.activo = true
""")
    Integer countActiveBarbers(Long tenantId, Long branchId);


    @Query("""
    select utr
    from UserTenantRole utr
    join fetch utr.tenant t
    left join fetch utr.branch b
    where utr.user.id = :userId
      and utr.tenant.id = :tenantId
""")
    Optional<UserTenantRole> findByUserIdAndTenantIdWithRelations(
            @Param("userId") Long userId,
            @Param("tenantId") Long tenantId
    );

}

