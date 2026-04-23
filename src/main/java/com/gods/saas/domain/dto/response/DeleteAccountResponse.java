package com.gods.saas.domain.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class DeleteAccountResponse {
    private boolean success;
    private String message;
}