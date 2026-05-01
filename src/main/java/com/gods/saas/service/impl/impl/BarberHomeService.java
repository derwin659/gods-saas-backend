package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.response.BarberHomeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface BarberHomeService {
    BarberHomeResponse getBarberHome(Authentication authentication);

    BarberHomeResponse uploadMyPhoto(Authentication authentication, MultipartFile file);

    BarberHomeResponse deleteMyPhoto(Authentication authentication);
}