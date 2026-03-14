package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Pantalla;
import com.gods.saas.domain.model.SesionIa;
import com.gods.saas.domain.repository.PantallaIARepository;
import com.gods.saas.domain.repository.SesionIARepository;
import com.gods.saas.utils.EstadoConexionPantalla;
import com.gods.saas.utils.EstadoPantalla;
import com.gods.saas.utils.EstadoSesion;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PantallaIAService {

    private final PantallaIARepository pantallaRepo;
    private final SesionIARepository sesionRepo;
    private final SesionIAService sesionService;
    private final PantallaSocketService socketService;

    // La TV pide la siguiente sesión
    public SesionIa obtenerSiguienteSesion(String pantallaId) {

        Pantalla pantalla = pantallaRepo.findById(pantallaId)
                .orElseThrow(() -> new IllegalStateException("Pantalla no existe"));

        if (pantalla.getEstadoConexion() != EstadoConexionPantalla.ONLINE) {
            throw new IllegalStateException("Pantalla offline");
        }

        // 🔴 CLAVE: solo asignar si está LIBRE
        if (pantalla.getEstadoPantalla() != EstadoPantalla.LIBRE) {
            return null;
        }

        SesionIa siguiente = sesionRepo
                .findFirstByEstadoOrderByCreadoEnAsc(EstadoSesion.EN_COLA)
                .orElse(null);

        if (siguiente == null) {
            return null;
        }

        // 👉 marcar sesión + pantalla
        sesionService.marcarMostrando(siguiente.getId(), pantallaId);

        // 👉 WS inicial
        socketService.enviarEventoPantalla(
                pantallaId,
                Map.of(
                        "evento", "MOSTRAR_RESULTADO_ANALITICO",
                        "sesionId", siguiente.getId()
                )
        );

        return siguiente;
    }

    public SesionIa obtenerSesionActualPantalla(String pantallaId) {

        Pantalla pantalla = pantallaRepo.findById(pantallaId)
                .orElseThrow(() -> new IllegalStateException("Pantalla no existe"));

        if (pantalla.getSesionActualId() == null) {
            return null;
        }

        return sesionRepo.findById(pantalla.getSesionActualId())
                .orElse(null);
    }

    @Transactional
    public SesionIa tomarSiguienteSesion(String pantallaId) {

        Pantalla pantalla = pantallaRepo.findById(pantallaId)
                .orElseThrow(() -> new IllegalStateException("Pantalla no existe"));

        if (pantalla.getEstadoPantalla() != EstadoPantalla.LIBRE) {
            return null;
        }

        SesionIa sesion = sesionRepo
                .findFirstByEstadoOrderByCreadoEnAsc(EstadoSesion.EN_COLA)
                .orElse(null);

        if (sesion == null) {
            return null;
        }

        // 🔥 ASIGNACIÓN REAL
        sesion.setEstado(EstadoSesion.MOSTRANDO_EN_TV);
        pantalla.setEstadoPantalla(EstadoPantalla.MOSTRANDO_SESION);
        pantalla.setSesionActualId(sesion.getId());

        sesionRepo.save(sesion);
        pantallaRepo.save(pantalla);

        return sesion;
    }



}