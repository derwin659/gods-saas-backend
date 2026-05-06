package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateAdminPermissionsRequest {
    private List<String> permissions;
}