package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class InactiveCustomerResponse {

    private Long customerId;
    private String nombre;
    private String telefono;
    private LocalDateTime ultimaVisita;
}