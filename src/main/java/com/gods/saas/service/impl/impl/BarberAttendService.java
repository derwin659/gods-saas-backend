package com.gods.saas.service.impl.impl;


import com.gods.saas.domain.dto.response.BarberServiceResponse;

import java.util.List;

public interface BarberAttendService {
    List<BarberServiceResponse> listServices(Long tenantId);
}
