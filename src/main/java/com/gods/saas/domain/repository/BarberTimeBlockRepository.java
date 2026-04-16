package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.BarberTimeBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BarberTimeBlockRepository extends JpaRepository<BarberTimeBlock, Long> {

    List<BarberTimeBlock> findByTenant_IdAndBranch_IdAndBarber_IdAndBlockDateOrderByStartTimeAsc(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate blockDate
    );

    List<BarberTimeBlock> findByTenant_IdAndBranch_IdAndBarber_IdOrderByBlockDateAscStartTimeAsc(
            Long tenantId,
            Long branchId,
            Long barberId
    );
}