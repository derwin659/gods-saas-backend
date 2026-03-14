package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.response.BarberHomeResponse;
import org.springframework.security.core.Authentication;

public interface BarberHomeService {
    BarberHomeResponse getBarberHome(Authentication authentication);
}
