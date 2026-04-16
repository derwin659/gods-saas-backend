package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateBarberTimeBlockRequest;
import com.gods.saas.domain.dto.response.BarberTimeBlockResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.BarberTimeBlock;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BarberTimeBlockRepository;
import com.gods.saas.domain.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerBarberTimeBlockService {

    private final BarberTimeBlockRepository barberTimeBlockRepository;
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public void createBlock(Long tenantId, Long branchId, CreateBarberTimeBlockRequest request) {
        if (request.getBarberUserId() == null) {
            throw new RuntimeException("El barbero es obligatorio");
        }

        AppUser barber = appUserRepository.findByIdAndTenant_Id(request.getBarberUserId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        if (barber.getBranch() == null || !barber.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("El barbero no pertenece a esta sucursal");
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

    @Transactional(readOnly = true)
    public List<BarberTimeBlockResponse> listBlocks(Long tenantId, Long branchId, Long barberUserId) {
        appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

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
    public void deleteBlock(Long tenantId, Long branchId, Long blockId) {
        BarberTimeBlock block = barberTimeBlockRepository.findById(blockId)
                .orElseThrow(() -> new RuntimeException("Bloqueo no encontrado"));

        if (!block.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El bloqueo no pertenece al tenant");
        }

        if (!block.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("El bloqueo no pertenece a la sucursal");
        }

        barberTimeBlockRepository.delete(block);
    }
}