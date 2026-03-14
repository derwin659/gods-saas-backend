package com.gods.saas.service.impl.impl;


import com.gods.saas.domain.dto.response.BarberAgendaItemResponse;

import java.time.LocalDate;
import java.util.List;

public interface BarberAgendaService {
    List<BarberAgendaItemResponse> getAgenda(Long tenantId, Long branchId, Long userId, LocalDate fecha);
}
