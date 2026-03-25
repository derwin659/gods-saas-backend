package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class RejectPaymentRequest {
    private String rejectedBy;
    private String reason;
}