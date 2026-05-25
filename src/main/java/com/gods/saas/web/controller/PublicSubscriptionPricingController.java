package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.SubscriptionPlanPriceResponse;
import com.gods.saas.service.impl.SubscriptionPlanPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/subscription-prices")
@RequiredArgsConstructor
public class PublicSubscriptionPricingController {

    private final SubscriptionPlanPricingService pricingService;

    @GetMapping
    public List<SubscriptionPlanPriceResponse> getPrices(
            @RequestParam(value = "country", required = false, defaultValue = "PE") String country
    ) {
        return pricingService.listMonthlyPricesForCountry(country);
    }
}