package com.gods.saas.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class UpdateUserBranchesRequest {
    private List<Long> branchIds;
}
