package com.gods.saas.service.impl.impl;


import com.gods.saas.domain.dto.response.ClientPointsResponse;
import org.springframework.security.core.Authentication;

public interface ClientPointsService {
    ClientPointsResponse getClientPoints(Authentication authentication);
}