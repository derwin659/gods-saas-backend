package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.BarberAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BarberAvailabilityRepository extends JpaRepository<BarberAvailability, Long> {

    List<BarberAvailability> findByTenant_IdAndBranch_IdAndBarber_IdOrderByDayOfWeekAscStartTimeAsc(
            Long tenantId,
            Long branchId,
            Long barberId
    );

    Optional<BarberAvailability> findByTenant_IdAndBranch_IdAndBarber_IdAndDayOfWeek(
            Long tenantId,
            Long branchId,
            Long barberId,
            Integer dayOfWeek
    );

    void deleteByTenant_IdAndBranch_IdAndBarber_Id(
            Long tenantId,
            Long branchId,
            Long barberId
    );

    @Query("""
        select count(distinct r.user.id)
        from UserTenantRole r
        where r.tenant.id = :tenantId
          and (:branchId is null or r.branch.id = :branchId)
          and r.role = com.gods.saas.domain.model.RoleType.BARBER
          and r.user.activo = true
          and not exists (
            select ba.id
            from BarberAvailability ba
            where ba.tenant.id = :tenantId
              and ba.barber.id = r.user.id
              and (:branchId is null or ba.branch.id = :branchId)
              and ba.isWorking = true
          )
        """)
    long countActiveBarbersWithoutSchedule(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId
    );}