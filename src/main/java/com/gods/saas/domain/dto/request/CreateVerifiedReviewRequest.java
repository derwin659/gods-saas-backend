package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.*;

public record CreateVerifiedReviewRequest(
        @NotNull Long appointmentId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String comment
) {}