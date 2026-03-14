package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CrearSesionRequest;
import com.gods.saas.domain.model.SesionIa;
import com.gods.saas.mapper.SesionIaPantallaMapper;
import com.gods.saas.service.impl.PantallaIAService;
import com.gods.saas.service.impl.SesionIAService;
import com.gods.saas.service.impl.SesionIaPantallaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ia/tv")
@RequiredArgsConstructor
@Slf4j
public class IaPantallaController {

    private final PantallaIAService pantallaService;
    private final SesionIAService sesionService;



    // =====================================================
    // ESTADO ACTUAL DE LA PANTALLA (READ ONLY)
    // =====================================================
    @GetMapping("/{pantallaId}/estado")
    public ResponseEntity<SesionIaPantallaDTO> estadoPantalla(
            @PathVariable String pantallaId
    ) {
        log.info("📺 TV pidió estado actual. pantallaId={}", pantallaId);

        SesionIa sesion = pantallaService.obtenerSesionActualPantalla(pantallaId);

        if (sesion == null) {
            log.info("📭 Pantalla [{}] sin sesión asignada", pantallaId);
            return ResponseEntity.noContent().build();
        }

        log.info("📥 Pantalla [{}] mostrando sesión [{}]", pantallaId, sesion.getId());

        return ResponseEntity.ok(
                SesionIaPantallaMapper.fromSesion(sesion)
        );
    }

    @PostMapping("/{pantallaId}/tomarsesion")
    public ResponseEntity<SesionIaPantallaDTO> tomarSesion(
            @PathVariable String pantallaId
    ) {
        log.info("📺 TV [{}] solicita tomar siguiente sesión", pantallaId);

        SesionIa sesion = pantallaService.tomarSiguienteSesion(pantallaId);

        if (sesion == null) {
            log.info("📭 No hay sesiones en cola");
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(
                SesionIaPantallaMapper.fromSesion(sesion)
        );
    }

}
