package com.gods.saas.domain.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CustomerFollowUpRequest {
    private String title;
    private String message;
    private String channel;
    private LocalDateTime scheduledAt;
}
