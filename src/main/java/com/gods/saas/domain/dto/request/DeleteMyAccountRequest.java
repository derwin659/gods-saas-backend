package com.gods.saas.domain.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteMyAccountRequest {

    private String currentPassword;
    private String confirmation;
}