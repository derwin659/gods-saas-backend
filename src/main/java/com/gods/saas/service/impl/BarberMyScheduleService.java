package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.BarberAvailabilityDayRequest;
import com.gods.saas.domain.dto.request.CreateBarberTimeBlockRequest;
import com.gods.saas.domain.dto.request.SaveBarberAvailabilityRequest;
import com.gods.saas.domain.dto.response.BarberAvailabilityDayResponse;
import com.gods.saas.domain.dto.response.BarberTimeBlockResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.BarberAvailability;
import com.gods.saas.domain.model.BarberTimeBlock;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BarberAvailabilityRepository;
import com.gods.saas.domain.repository.BarberTimeBlockRepository;
import com.gods.saas.domain.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarberMyScheduleService {

    private final BarberAvailabilityRepository barberAvailabilityRepository;
    private final BarberTimeBlockRepository barberTimeBlockRepository;
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<BarberAvailabilityDayResponse> getMyAvailability(
            Long tenantId,
            Long branchId,
            Long barberUserId
    ) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        validateBarberBelongsToBranch(barber, branchId);

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

    @Transactional
    public void saveMyAvailability(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            SaveBarberAvailabilityRequest request
    ) {
        if (request.getDays() == null || request.getDays().isEmpty()) {
            throw new RuntimeException("Debes enviar al menos un día");
        }

        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        validateBarberBelongsToBranch(barber, branchId);

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
                tenantId, branchId, barberUserId
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
    public List<BarberTimeBlockResponse> listMyBlocks(
            Long tenantId,
            Long branchId,
            Long barberUserId
    ) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        validateBarberBelongsToBranch(barber, branchId);

        return barberTimeBlockRepository
                .findByTenant_IdAndBranch_IdAndBarber_IdOrderByBlockDateAscStartTimeAsc(
                        tenantId, branchId, barberUserId
                )
                .stream()
                .map(b -> BarberTimeBlockResponse.builder()
                        .id(b.getId())
                        .barberUserId(b.getBarber().getId())
                        .blockDate(b.getBlockDate().toString())
                        .startTime(b.getStartTime().toString())
                        .endTime(b.getEndTime().toString())
                        .allDay(b.getAllDay())
                        .reason(b.getReason())
                        .build())
                .toList();
    }

    @Transactional
    public void createMyBlock(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            CreateBarberTimeBlockRequest request
    ) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        validateBarberBelongsToBranch(barber, branchId);

        if (request.getBlockDate() == null || request.getBlockDate().isBlank()) {
            throw new RuntimeException("La fecha es obligatoria");
        }

        LocalDate blockDate = LocalDate.parse(request.getBlockDate());
        boolean allDay = Boolean.TRUE.equals(request.getAllDay());

        LocalTime startTime = allDay ? LocalTime.of(0, 0) : LocalTime.parse(request.getStartTime());
        LocalTime endTime = allDay ? LocalTime.of(23, 59) : LocalTime.parse(request.getEndTime());

        if (!startTime.isBefore(endTime)) {
            throw new RuntimeException("La hora inicio debe ser menor a la hora fin");
        }

        BarberTimeBlock block = BarberTimeBlock.builder()
                .tenant(branch.getTenant())
                .branch(branch)
                .barber(barber)
                .blockDate(blockDate)
                .startTime(startTime)
                .endTime(endTime)
                .allDay(allDay)
                .reason(request.getReason())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        barberTimeBlockRepository.save(block);
    }

    @Transactional
    public void deleteMyBlock(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            Long blockId
    ) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        validateBarberBelongsToBranch(barber, branchId);

        BarberTimeBlock block = barberTimeBlockRepository.findById(blockId)
                .orElseThrow(() -> new RuntimeException("Bloqueo no encontrado"));

        if (!block.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El bloqueo no pertenece al tenant");
        }

        if (!block.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("El bloqueo no pertenece a la sucursal");
        }

        if (!block.getBarber().getId().equals(barberUserId)) {
            throw new RuntimeException("No puedes eliminar un bloqueo de otro barbero");
        }

        barberTimeBlockRepository.delete(block);
    }

    private void validateBarberBelongsToBranch(AppUser barber, Long branchId) {
        if (barber.getBranch() == null || !barber.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("El barbero no pertenece a esta sucursal");
        }
    }
}