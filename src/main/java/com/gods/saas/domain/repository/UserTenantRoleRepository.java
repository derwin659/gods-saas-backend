package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.UserTenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserTenantRoleRepository extends JpaRepository<UserTenantRole, Long> {


    boolean existsByUserIdAndTenantIdAndRoleIn(
            Long userId,
            Long tenantId,
            Collection<RoleType> roles
    );

    boolean existsByUserIdAndTenantIdAndBranchIdAndRoleIn(
            Long userId,
            Long tenantId,
            Long branchId,
            Collection<RoleType> roles
    );

    List<UserTenantRole> findByUserId(Long userId);

    @Query("""
    select utr
    from UserTenantRole utr
    join fetch utr.tenant t
    left join fetch utr.branch b
    where utr.user.id = :userId
    order by utr.id asc
""")
    List<UserTenantRole> findByUserIdWithRelations(@Param("userId") Long userId);

    Optional<UserTenantRole> findFirstByUserIdAndTenantIdOrderByIdAsc(Long userId, Long tenantId);

    List<UserTenantRole> findByTenantId(Long tenantId);

    List<UserTenantRole> findByUser_Id(Long userId);

    Optional<UserTenantRole> findFirstByUser_IdAndTenant_IdOrderByIdAsc(Long userId, Long tenantId);

    List<UserTenantRole> findByTenant_Id(Long tenantId);

    boolean existsByUser_IdAndTenant_IdAndRole(Long userId, Long tenantId, RoleType role);

    void deleteByUser_IdAndTenant_IdAndRole(Long userId, Long tenantId, RoleType role);

    @Query("""
    select utr
    from UserTenantRole utr
    left join fetch utr.branch b
    where utr.user.id = :userId
      and utr.tenant.id = :tenantId
      and utr.role = :role
    order by b.nombre asc
""")
    List<UserTenantRole> findByUserIdAndTenantIdAndRoleWithBranch(
            @Param("userId") Long userId,
            @Param("tenantId") Long tenantId,
            @Param("role") RoleType role
    );

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
    left join utr.user u
    left join u.branch primaryBranch
    where utr.user.id = :userId
      and utr.tenant.id = :tenantId
      and (primaryBranch is null or b.id = primaryBranch.id)
""")
    Optional<UserTenantRole> findByUserIdAndTenantIdWithRelations(
            @Param("userId") Long userId,
            @Param("tenantId") Long tenantId
    );

    List<UserTenantRole> findByTenant_IdAndBranch_Id(Long tenantId, Long branchId);


    boolean existsByUser_IdAndTenant_IdAndBranch_Id(Long userId, Long tenantId, Long branchId);


    @Query("""
    select distinct r.user
    from UserTenantRole r
    join r.user u
    where r.tenant.id = :tenantId
      and (:branchId is null or r.branch.id = :branchId)
      and r.role = :role
      and u.activo = true
    order by u.nombre asc
""")
    List<com.gods.saas.domain.model.AppUser> findActiveUsersByTenantBranchAndRole(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("role") RoleType role
    );

}
