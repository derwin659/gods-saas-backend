package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.BarberAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
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
}