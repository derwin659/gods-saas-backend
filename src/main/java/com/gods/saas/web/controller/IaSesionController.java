package com.gods.saas.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gods.saas.domain.dto.request.CrearSesionRequest;
import com.gods.saas.domain.dto.request.GenerarImagenRequest;
import com.gods.saas.domain.dto.request.SeleccionClienteRequest;
import com.gods.saas.domain.dto.response.SeleccionClienteResponse;
import com.gods.saas.domain.dto.response.UxAnalisisResponse;
import com.gods.saas.domain.model.Pantalla;
import com.gods.saas.domain.model.AiGenerationJob;
import com.gods.saas.domain.model.SesionIa;
import com.gods.saas.service.impl.PantallaIAService;
import com.gods.saas.service.impl.SesionIAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia/sesiones")
@RequiredArgsConstructor
public class IaSesionController {

    private final SesionIAService sesionService;
    private final PantallaIAService pantallaService;

    @GetMapping("/pantallas")
    public List<Pantalla> listarPantallas(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long branchId
    ) {
        return pantallaService.listarPantallas(tenantId, branchId);
    }
    // =====================================================
    // 1️⃣ CREAR SESIÓN (recepción / barbero)
    // =====================================================
    @PostMapping("/create")
    public SesionIa crearSesion(@RequestBody CrearSesionRequest request) {
        return sesionService.crearSesion(request);
    }

    // =====================================================
    // 2️⃣ ENVIAR SESIÓN A COLA
    // CREADA → EN_COLA
    // =====================================================
    @PostMapping("/{sesionId}/enviar-cola")
    public Map<String, Object> enviarACola(@PathVariable String sesionId) {
        return sesionService.enviarACola(sesionId);
    }

    // =====================================================
    // 3️⃣ LA TV TOMA LA SESIÓN
    // EN_COLA → MOSTRANDO_EN_TV
    // =====================================================
    @PostMapping("/{sesionId}/mostrar/{pantallaId}")
    public void mostrarEnTv(
            @PathVariable String sesionId,
            @PathVariable String pantallaId
    ) {
        sesionService.marcarMostrando(sesionId, pantallaId);
    }

    // =====================================================
    // 4️⃣ IA ANALÍTICA (rostro, recomendaciones)

    // =====================================================
    @PostMapping("/{sesionId}/analitica")
    public UxAnalisisResponse ejecutarAnalitica(
            @PathVariable String sesionId,
            @RequestBody Map<String, String> body
    ) throws JsonProcessingException {

        String imagenBase64 = body.get("imagenBase64");
        return sesionService.ejecutarAnalitica(sesionId, imagenBase64);
    }

    // =====================================================
    // 5️⃣ CLIENTE SELECCIONA OPCIÓN FINAL
    // MOSTRANDO_EN_TV → SELECCIONADA
    // =====================================================
    @PostMapping("/{sesionId}/seleccion")
    public SeleccionClienteResponse seleccionar(
            @PathVariable String sesionId,
            @RequestBody SeleccionClienteRequest request
    ) throws JsonProcessingException {

        sesionService.registrarSeleccion(sesionId, request);

        SeleccionClienteResponse resp = new SeleccionClienteResponse();
        resp.setSesionId(sesionId);
        resp.setEstado("SELECCIONADA");
        resp.setMensaje("Selección guardada correctamente");

        return resp;
    }

    // =====================================================
    // 6️⃣ IA ILUSTRATIVA (preview / imágenes)
    // SELECCIONADA → GENERANDO_IMAGEN → MOSTRANDO_EN_TV
    // =====================================================
    @PostMapping("/{sesionId}/preview")
    public ResponseEntity<Map<String, Object>> generarPreview(@PathVariable String sesionId,@RequestBody GenerarImagenRequest generarImagenRequest) throws JsonProcessingException {
        String jobId = sesionService.generarPreview(sesionId, generarImagenRequest);
        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "sessionId", sesionId,
                "status", "QUEUED"
        ));
    }

    @GetMapping("/{sesionId}/preview-jobs/{jobId}")
    public Map<String, Object> obtenerEstadoPreview(
            @PathVariable String sesionId,
            @PathVariable String jobId
    ) {
        AiGenerationJob job = sesionService.obtenerTrabajoGeneracion(sesionId, jobId);
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jobId", job.getId());
        response.put("sessionId", job.getSessionId());
        response.put("status", job.getStatus());
        response.put("attempts", job.getAttempts());
        response.put("createdAt", job.getCreatedAt());
        response.put("startedAt", job.getStartedAt());
        response.put("completedAt", job.getCompletedAt());
        response.put("error", job.getErrorMessage());
        return response;
    }
    // =====================================================
    // 7️⃣ FINALIZAR SESIÓN
    // MOSTRANDO_EN_TV → FINALIZADA
    // =====================================================
    @PostMapping("/{sesionId}/finalizar")
    public void finalizar(@PathVariable String sesionId) {
        sesionService.finalizar(sesionId);
    }

}
