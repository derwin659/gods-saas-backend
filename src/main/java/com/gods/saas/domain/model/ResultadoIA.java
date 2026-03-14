package com.gods.saas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class ResultadoIA {

    @Id
    @GeneratedValue
    private Long id;

    private String sesionId;

    @Column(columnDefinition = "jsonb")
    private String resultadoAnalitico; // forma rostro, cortes, tintes

    @Column(columnDefinition = "jsonb")
    private String resultadoGenerativo; // URLs frontal/lateral/trasera

    private LocalDateTime generadoEn;
}

