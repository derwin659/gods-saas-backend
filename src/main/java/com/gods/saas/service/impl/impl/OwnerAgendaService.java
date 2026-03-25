package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.response.OwnerAgendaResponse;

import java.time.LocalDate;
import java.util.List;

public interface OwnerAgendaService {
    List<OwnerAgendaResponse> getAgendaDelDia(Long tenantId, Long branchId, LocalDate fecha);
}