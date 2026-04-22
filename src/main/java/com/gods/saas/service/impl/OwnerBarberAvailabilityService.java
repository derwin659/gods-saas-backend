package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.BarberAvailabilityDayRequest;
import com.gods.saas.domain.dto.request.SaveBarberAvailabilityRequest;
import com.gods.saas.domain.dto.response.BarberAvailabilityDayResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.BarberAvailability;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BarberAvailabilityRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerBarberAvailabilityService {

    private final BarberAvailabilityRepository barberAvailabilityRepository;
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    @Transactional
    public void saveAvailability(Long tenantId, Long branchId, SaveBarberAvailabilityRequest request) {
        if (request.getBarberUserId() == null) {
            throw new RuntimeException("El barbero es obligatorio");
        }

        if (request.getDays() == null || request.getDays().isEmpty()) {
            throw new RuntimeException("Debes enviar al menos un día");
        }

        AppUser barber = appUserRepository.findByIdAndTenant_Id(request.getBarberUserId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        boolean belongsToBranch = userTenantRoleRepository.existsByUser_IdAndTenant_IdAndBranch_Id(
                barber.getId(),
                tenantId,
                branchId
        );

        if (!belongsToBranch) {
            throw new RuntimeException("El barbero no pertenece a esta sucursal");
        }

        for (BarberAvailabilityDayRequest day : request.getDays()) {
            if (day.getDayOfWeek() == null || day.getDayOfWeek() < 1 || day.getDayOfWeek() > 7) {
                throw new RuntimeException("dayOfWeek debe estar entre 1 y 7");
            }

            boolean isWorking = Boolean.TRUE.equals(day.getIsWorking());
            if (isWorking) {
                if (day.getStartTime() == null || day.getEndTime() == null) {
                    throw new RuntimeException("Los días laborables deben tener hora de inicio y fin");
                }
                if (!day.getStartTime().isBefore(day.getEndTime())) {
                    throw new RuntimeException("La hora inicio debe ser menor a la hora fin");
                }
            }
        }

        barberAvailabilityRepository.deleteByTenant_IdAndBranch_IdAndBarber_Id(
                tenantId, branchId, barber.getId()
        );

        for (BarberAvailabilityDayRequest day : request.getDays()) {
            boolean isWorking = Boolean.TRUE.equals(day.getIsWorking());

            BarberAvailability availability = BarberAvailability.builder()
                    .tenant(branch.getTenant())
                    .branch(branch)
                    .barber(barber)
                    .dayOfWeek(day.getDayOfWeek())
                    .isWorking(isWorking)
                    .startTime(isWorking ? day.getStartTime() : LocalTime.of(0, 0))
                    .endTime(isWorking ? day.getEndTime() : LocalTime.of(0, 0))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            barberAvailabilityRepository.save(availability);
        }
    }

    @Transactional(readOnly = true)
    public List<BarberAvailabilityDayResponse> getAvailability(Long tenantId, Long branchId, Long barberUserId) {
        appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        boolean belongsToBranch = userTenantRoleRepository.existsByUser_IdAndTenant_IdAndBranch_Id(
                barberUserId,
                tenantId,
                branch.getId()
        );

        if (!belongsToBranch) {
            throw new RuntimeException("El barbero no pertenece a esta sucursal");
        }

        return barberAvailabilityRepository
                .findByTenant_IdAndBranch_IdAndBarber_IdOrderByDayOfWeekAscStartTimeAsc(
                        tenantId, branchId, barberUserId
                )
                .stream()
                .sorted(Comparator.comparing(BarberAvailability::getDayOfWeek))
                .map(a -> BarberAvailabilityDayResponse.builder()
                        .dayOfWeek(a.getDayOfWeek())
                        .isWorking(a.getIsWorking())
                        .startTime(a.getStartTime() != null ? a.getStartTime().toString() : null)
                        .endTime(a.getEndTime() != null ? a.getEndTime().toString() : null)
                        .build())
                .toList();
    }
}