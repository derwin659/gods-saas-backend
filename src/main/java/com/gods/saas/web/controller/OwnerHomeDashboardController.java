package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.OwnerHomeDashboardResponse;
import com.gods.saas.service.impl.impl.OwnerHomeDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/owner/home")
@RequiredArgsConstructor
public class OwnerHomeDashboardController {

    private final OwnerHomeDashboardService ownerHomeDashboardService;

    @GetMapping("/dashboard")
    public OwnerHomeDashboardResponse getDashboard(
            Authentication authentication,
            @RequestParam(required = false) Long branchId
    ) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = toLong(details.get("tenantId"));

        return ownerHomeDashboardService.getDashboard(tenantId, branchId);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }
}