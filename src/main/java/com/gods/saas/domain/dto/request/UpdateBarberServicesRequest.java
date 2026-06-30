package com.gods.saas.domain.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class UpdateBarberServicesRequest {
    private List<Long> serviceIds;
}
