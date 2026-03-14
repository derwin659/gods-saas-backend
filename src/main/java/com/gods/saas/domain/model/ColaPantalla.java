package com.gods.saas.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class ColaPantalla {

    @Id
    @GeneratedValue
    private Long id;

    private String pantallaId;

    private String sesionId;

    private Integer posicion;

    private LocalDateTime agregadoEn;
}

