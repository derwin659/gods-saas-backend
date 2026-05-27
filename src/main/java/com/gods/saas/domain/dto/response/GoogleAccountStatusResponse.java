package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoogleAccountStatusResponse {
    private boolean linked;
    private String email;
    private String name;
    private String pictureUrl;
    private String linkedAt;
}
