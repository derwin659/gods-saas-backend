package com.gods.saas.domain.dto.request;


import lombok.Data;

@Data
public class ApprovesPaymentRequest {
    private String approvedBy;
    private String notes;
}