package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.*;

public record CreateVerifiedReviewRequest(
        Long appointmentId,
        Long saleId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String comment
) {}