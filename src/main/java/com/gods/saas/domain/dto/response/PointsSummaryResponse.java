package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PointsSummaryResponse {
    private int disponibles;
    private int metaCorteGratis;
    private int faltanParaCorteGratis;
    private double progresoCorteGratis;

    private boolean puedeCanjearAlgo;
    private String premioDisponibleAhora;
    private Integer puntosPremioDisponible;

    public PointsSummaryResponse(
            int disponibles,
            int metaCorteGratis,
            int faltanParaCorteGratis,
            double progresoCorteGratis,
            boolean puedeCanjearAlgo,
            String premioDisponibleAhora,
            Integer puntosPremioDisponible
    ) {
        this.disponibles = disponibles;
        this.metaCorteGratis = metaCorteGratis;
        this.faltanParaCorteGratis = faltanParaCorteGratis;
        this.progresoCorteGratis = progresoCorteGratis;
        this.puedeCanjearAlgo = puedeCanjearAlgo;
        this.premioDisponibleAhora = premioDisponibleAhora;
        this.puntosPremioDisponible = puntosPremioDisponible;
    }

    public int getDisponibles() {
        return disponibles;
    }

    public int getMetaCorteGratis() {
        return metaCorteGratis;
    }

    public int getFaltanParaCorteGratis() {
        return faltanParaCorteGratis;
    }

    public double getProgresoCorteGratis() {
        return progresoCorteGratis;
    }

    public boolean isPuedeCanjearAlgo() {
        return puedeCanjearAlgo;
    }

    public String getPremioDisponibleAhora() {
        return premioDisponibleAhora;
    }

    public Integer getPuntosPremioDisponible() {
        return puntosPremioDisponible;
    }
}
