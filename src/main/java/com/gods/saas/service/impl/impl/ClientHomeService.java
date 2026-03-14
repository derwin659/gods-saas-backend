package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.response.ClientHomeResponse;
import org.springframework.security.core.Authentication;

public interface ClientHomeService {
    ClientHomeResponse getClientHome(Authentication authentication);
}
