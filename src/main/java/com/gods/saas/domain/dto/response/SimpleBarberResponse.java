package com.gods.saas.domain.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleBarberResponse {
    private Long id;
    private String nombre;
    private String email;
}