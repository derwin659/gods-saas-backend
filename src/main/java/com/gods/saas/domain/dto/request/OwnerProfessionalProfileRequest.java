package com.gods.saas.domain.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class OwnerProfessionalProfileRequest {
    private List<Long> branchIds;
    private Boolean allBranches = false;
}
