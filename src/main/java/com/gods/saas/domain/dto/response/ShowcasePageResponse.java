package com.gods.saas.domain.dto.response;

import java.util.List;

public record ShowcasePageResponse(
        List<ShowcaseResponse> items,
        int page,
        int size,
        boolean hasMore,
        long total
) {}