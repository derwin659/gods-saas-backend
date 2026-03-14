package com.gods.saas.web.controller;


import com.gods.saas.domain.dto.request.QuickRegisterCustomerRequest;
import com.gods.saas.domain.dto.response.BarberServiceResponse;
import com.gods.saas.domain.dto.response.CustomerLookupResponse;
import com.gods.saas.service.impl.BarberAttendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/barber/attend")
@RequiredArgsConstructor
public class BarberAttendController {

    private final BarberAttendService barberAttendService;

    @GetMapping("/customer-by-phone")
    public CustomerLookupResponse findCustomerByPhone(
            @RequestParam Long tenantId,
            @RequestParam String phone
    ) {
        return barberAttendService.findCustomerByPhone(tenantId, phone);
    }

    @PostMapping("/customers/quick-register")
    public CustomerLookupResponse quickRegister(
            @Valid @RequestBody QuickRegisterCustomerRequest request
    ) {
        return barberAttendService.quickRegister(request);
    }

    @GetMapping("/services")
    public List<BarberServiceResponse> listServices(@RequestParam Long tenantId) {
        return barberAttendService.listServices(tenantId);
    }
}
