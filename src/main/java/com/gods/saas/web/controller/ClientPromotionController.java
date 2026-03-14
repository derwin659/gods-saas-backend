package com.gods.saas.web.controller;


import com.gods.saas.domain.dto.response.ClientPromotionResponse;
import com.gods.saas.service.impl.impl.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clients/promos")
public class ClientPromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public List<ClientPromotionResponse> getClientPromotions(Authentication authentication) {
        String idCustomer = authentication.getName();

        return promotionService.getClientPromotions(idCustomer);
    }
}
