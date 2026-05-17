package com.gods.saas.domain.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewDemoRequest {

    private String notes;
    private Long reviewedBy;
}