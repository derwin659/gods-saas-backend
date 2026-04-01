package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.AiRecommendation;
import org.springframework.transaction.annotation.Propagation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.client.IaAnaliticaClient;
import com.gods.saas.client.IaIlustrativaClient;
import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.model.Pantalla;
import com.gods.saas.domain.model.SesionIa;
import com.gods.saas.domain.repository.PantallaIARepository;
import com.gods.saas.domain.repository.SesionIARepository;
import com.gods.saas.mapper.IaAnaliticaUxMapper;
import com.gods.saas.socket.EventoSocket;
import com.gods.saas.socket.EventoSocketPublisher;
import com.gods.saas.utils.EstadoPantalla;
import com.gods.saas.utils.EstadoSesion;
import com.gods.saas.utils.TipoEventoSocket;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SesionIAService {

    private final SesionIARepository sesionRepo;
    private final PantallaIARepository pantallaRepo;
    private final EventoSocketPublisher eventoPublisher;
    private final PantallaSocketService pantallaSocketService;
    private final IaAnaliticaClient iaAnaliticaClient;
    private final IaIlustrativaClient iaIlustrativaClient;
    private final ObjectMapper objectMapper;

    private final ObjectMapper mapper = new ObjectMapper();

    // =========================================================
    // 1) CREAR SESIÓN (recepción / barbero)
    // Estado inicial: CREADA
    // =========================================================
    public SesionIa crearSesion(CrearSesionRequest req) {
        SesionIa sesion = new SesionIa();
        System.out.printf("entro aqui");

        sesion.setTenantId(req.getTenantId());
        sesion.setSucursalId(req.getSucursalId());
        sesion.setBarberoId(req.getBarberoId()); // puede ser null
        // temporal mientras no definas barbero en el request
        System.out.printf("noo llego aqui");
        sesion.setEstado(EstadoSesion.CREADA);
        sesion.setCreadoEn(LocalDateTime.now());
        return sesionRepo.save(sesion);
    }

    // =========================================================
    // 2) ENVIAR A COLA (cuando ya hay sesión creada)
    // CREADA -> EN_COLA
    // =========================================================
    public Map<String, Object> enviarACola(String sesionId) {
        SesionIa sesion = obtenerSesion(sesionId);
        validarEstado(sesion, EstadoSesion.CREADA);

        sesion.setEstado(EstadoSesion.EN_COLA);
        sesionRepo.save(sesion);

        long enCola = sesionRepo.countByEstado(EstadoSesion.EN_COLA);

        return Map.of(
                "estado", "EN_COLA",
                "sesionId", sesionId,
                "posicion", enCola
        );
    }

    // =========================================================
    // 3) LA TV (PANTALLA) TOMA UNA SESIÓN DE COLA
    // EN_COLA -> MOSTRANDO_EN_TV
    //
    // Aquí es donde DEBE ocurrir MOSTRANDO_EN_TV (y solo aquí).
    // =========================================================
    public void marcarMostrando(String sesionId, String pantallaId) {
        SesionIa sesion = obtenerSesion(sesionId);
        Pantalla pantalla = obtenerPantalla(pantallaId);

        validarEstado(sesion, EstadoSesion.EN_COLA);

        sesion.setEstado(EstadoSesion.MOSTRANDO_EN_TV);

        pantalla.setEstadoPantalla(EstadoPantalla.MOSTRANDO_SESION);
        pantalla.setSesionActualId(sesionId);

        sesionRepo.save(sesion);
        pantallaRepo.save(pantalla);

        // Notificar a TV: ya está la sesión en pantalla
        pantallaSocketService.enviarEventoPantalla(
                pantallaId,
                Map.of(
                        "evento", "SESION_EN_PANTALLA",
                        "sesionId", sesionId
                )
        );

        // (Opcional) evento general
        eventoPublisher.publicar(
                EventoSocket.of(
                        TipoEventoSocket.SESION_EN_PANTALLA,
                        sesionId,
                        null
                )
        );
    }

    // =========================================================
    // 4) EJECUTAR IA ANALÍTICA
    // Requiere: MOSTRANDO_EN_TV
    // (La analítica ocurre mientras ya está visible la sesión en TV)
    // =========================================================
    public UxAnalisisResponse ejecutarAnalitica(String sesionId, String imagenBase64) throws JsonProcessingException {
        SesionIa sesion = obtenerSesion(sesionId);
        validarEstadosPermitidos(sesion, EstadoSesion.CREADA, EstadoSesion.MOSTRANDO_EN_TV);

        AnalizarImagenRequest request = AnalizarImagenRequest.builder()
                .imagenBase64(imagenBase64)
                .contexto(new ContextoAnalisis(
                        sesion.getTenantId(),
                        sesion.getSucursalId()
                ))
                .build();

        AnalizarImagenResponse response;
        try {
            response = iaAnaliticaClient.analizarImagen(request);
        } catch (Exception e) {
            response = construirRespuestaMock();
        }

        sesion.setResultadoAnalitico(mapper.writeValueAsString(response));
        sesionRepo.save(sesion);

        if (sesion.getEstado() == EstadoSesion.MOSTRANDO_EN_TV) {
            String pantallaId = obtenerPantallaIdPorSesion(sesionId);
            pantallaSocketService.enviarEventoPantalla(
                    pantallaId,
                    Map.of(
                            "evento", "RESULTADO_ANALITICO_LISTO",
                            "sesionId", sesionId,
                            "resultado", response
                    )
            );
        }

        return IaAnaliticaUxMapper.toUx(response);
    }

    private AnalizarImagenResponse construirRespuestaMock() {

        FormaRostroDto formaRostro = FormaRostroDto.builder()
                .principal("ovalado")
                .alternativa("alargado")
                .confianza(0.93)
                .build();

        OnduladoDto onduladoDto = OnduladoDto.builder()
                .tipo("clasico")
                .apto(true)
                .build();

        CabelloDto cabello = CabelloDto.builder()
                .densidad("media")
                .ondulado(onduladoDto)
                .build();

        List<CorteRecomendadoDto> cortes = List.of(
                CorteRecomendadoDto.builder()
                        .nombre("Fade clásico")
                        .score(0.95)
                        .riesgo("bajo")
                        .build(),
                CorteRecomendadoDto.builder()
                        .nombre("Taper moderno")
                        .score(0.90)
                        .riesgo("bajo")
                        .build(),
                CorteRecomendadoDto.builder()
                        .nombre("Crop texturizado")
                        .score(0.84)
                        .riesgo("medio")
                        .build()
        );

        RecomendacionesDto recomendaciones = RecomendacionesDto.builder()
                .topRecomendado(cortes.get(0))
                .cortes(cortes)
                .tintes(List.of("Negro natural", "Castaño oscuro"))
                .build();

        MetaAnalisisDto meta = MetaAnalisisDto.builder().build();

        return AnalizarImagenResponse.builder()
                .formaRostro(formaRostro)
                .cabello(cabello)
                .recomendaciones(recomendaciones)
                .meta(meta)
                .build();
    }

    private void validarEstadosPermitidos(SesionIa sesion, EstadoSesion... permitidos) {
        for (EstadoSesion estado : permitidos) {
            if (sesion.getEstado() == estado) {
                return;
            }
        }
        throw new IllegalStateException(
                "Estado inválido. Permitidos: " + java.util.Arrays.toString(permitidos) +
                        ", actual: " + sesion.getEstado()
        );
    }

    // =========================================================
    // 5) CLIENTE SELECCIONA (corte/tinte/ondulado)
    // Requiere: MOSTRANDO_EN_TV
    // MOSTRANDO_EN_TV -> SELECCIONADA
    // =========================================================
    public void registrarSeleccion(String sesionId, SeleccionClienteRequest seleccion) throws JsonProcessingException {
        SesionIa sesion = obtenerSesion(sesionId);
        validarEstado(sesion, EstadoSesion.MOSTRANDO_EN_TV);

        // Guardar selección
        sesion.setSeleccionCliente(mapper.writeValueAsString(seleccion));
        sesion.setEstado(EstadoSesion.SELECCIONADA);
        sesionRepo.save(sesion);

        String pantallaId = obtenerPantallaIdPorSesion(sesionId);

        // Notificar a TV (opcional)
        pantallaSocketService.enviarEventoPantalla(
                pantallaId,
                Map.of(
                        "evento", "CLIENTE_SELECCIONO",
                        "sesionId", sesionId,
                        "seleccion", seleccion
                )
        );

        eventoPublisher.publicar(
                EventoSocket.of(
                        TipoEventoSocket.SELECCION_REALIZADA,
                        sesionId,
                        sesion.getSeleccionCliente()
                )
        );
    }


    // =========================================================
    // 6) GENERAR PREVIEW / IA ILUSTRATIVA
    // Requiere: SELECCIONADA
    // SELECCIONADA -> GENERANDO_IMAGEN -> MOSTRANDO_EN_TV
    // (vuelve a MOSTRANDO_EN_TV porque ahora está mostrando el resultado)
    // =========================================================
    public void generarPreview(String sesionId, GenerarImagenRequest generarImagenRequest) throws JsonProcessingException {

        SesionIa sesion = obtenerSesion(sesionId);
        validarEstado(sesion, EstadoSesion.SELECCIONADA);

        String pantallaId = obtenerPantallaIdPorSesion(sesionId);
        Pantalla pantalla = obtenerPantalla(pantallaId);

        // 1️⃣ Estado GENERANDO
        sesion.setEstado(EstadoSesion.GENERANDO_IMAGEN);
        pantalla.setEstadoPantalla(EstadoPantalla.GENERANDO_IMAGEN);
        sesionRepo.save(sesion);
        pantallaRepo.save(pantalla);

        // 2️⃣ Notificar TV (loader)
        pantallaSocketService.enviarEventoPantalla(
                pantallaId,
                Map.of(
                        "evento", "GENERANDO_IMAGEN",
                        "sesionId", sesionId
                )
        );

        eventoPublisher.publicar(
                EventoSocket.of(
                        TipoEventoSocket.GENERANDO_IMAGEN,
                        sesionId,
                        null
                )
        );


        sesionRepo.saveAndFlush(sesion);

        ejecutarGeneracionImagenAsync(sesionId, generarImagenRequest);
    }

    @Async
    public void ejecutarGeneracionImagenAsync(String sesionId, GenerarImagenRequest generarImagenRequest) {
        log.info("Ejecutando generacion de imagen {}", generarImagenRequest.getImagenes().toString());

        try {
            SesionIa sesion = obtenerSesion(sesionId);
            String pantallaId = obtenerPantallaIdPorSesion(sesionId);

            // =========================
            // 1️⃣ Parsear selección JSON
            // =========================
            Map<String, Object> seleccion =
                    objectMapper.readValue(
                            sesion.getSeleccionCliente(),
                            new TypeReference<>() {}
                    );

            CorteDTO corte = objectMapper.convertValue(seleccion.get("corte"), CorteDTO.class);
            corte.setNombre("mid fade");
            corte.setTipo("MID_FADE");

            TinteDTO tinte = objectMapper.convertValue(seleccion.get("tinte"), TinteDTO.class);
            OnduladoDTO ondulado = objectMapper.convertValue(seleccion.get("ondulado"), OnduladoDTO.class);

            // =========================
            // 2️⃣ Construir request IA
            // =========================
            GenerarImagenRequest request = new GenerarImagenRequest();
            request.setSesionId(sesionId);
            log.info("frontal {} ", generarImagenRequest.getImagenes().getFrontal().toString());
            request.setImagenes(generarImagenRequest.getImagenes());
            request.setCorte(corte);
            request.setTinte(tinte);
            request.setOndulado(ondulado);
            request.setVistas(List.of("frontal", "lateral", "trasera"));
            sesion.setEstado(EstadoSesion.GENERANDO_IMAGEN);
            sesionRepo.saveAndFlush(sesion);
            // =========================
            // 3️⃣ 🔥 LLAMADA REAL A PYTHON
            // =========================
            log.info("request para generar imagen{}",request.toString());
            GenerarImagenResponse response =
                    iaIlustrativaClient.generarImagen(request);
            log.info("respuesta de python imagen{}",response.toString());
            // =========================
            // 4️⃣ Guardar resultado
            // =========================
            sesion.setEstado(EstadoSesion.IMAGEN_GENERADA);

            sesion.setImagenes(
                    objectMapper.writeValueAsString(response.getImagenes())
            );

            sesionRepo.saveAndFlush(sesion);

            // =========================
            // 5️⃣ Notificar TV (resultado)
            // =========================
            pantallaSocketService.enviarEventoPantalla(
                    pantallaId,
                    Map.of(
                            "evento", "IMAGEN_GENERADA",
                            "sesionId", sesionId,
                            "imagenes", response
                    )
            );

            eventoPublisher.publicar(
                    EventoSocket.of(
                            TipoEventoSocket.IMAGEN_GENERADA,
                            sesionId,
                            response
                    )
            );

        } catch (Exception e) {
           e.getMessage();
        }
    }


    // =========================================================
    // 7) FINALIZAR SESIÓN
    // MOSTRANDO_EN_TV -> FINALIZADA (cuando ya terminó el flujo)
    // =========================================================
    public void finalizar(String sesionId) {
        SesionIa sesion = obtenerSesion(sesionId);

        validarEstadosPermitidos(
                sesion,
                EstadoSesion.CREADA,
                EstadoSesion.MOSTRANDO_EN_TV,
                EstadoSesion.SELECCIONADA,
                EstadoSesion.GENERANDO_IMAGEN,
                EstadoSesion.IMAGEN_GENERADA
        );

        sesion.setEstado(EstadoSesion.FINALIZADA);

        var pantallaOpt = pantallaRepo.findBySesionActualId(sesionId);
        if (pantallaOpt.isPresent()) {
            Pantalla pantalla = pantallaOpt.get();
            pantalla.setEstadoPantalla(EstadoPantalla.LIBRE);
            pantalla.setSesionActualId(null);
            pantallaRepo.save(pantalla);

            pantallaSocketService.enviarEventoPantalla(
                    pantalla.getId(),
                    Map.of(
                            "evento", "SESION_FINALIZADA",
                            "sesionId", sesionId
                    )
            );
        }

        sesionRepo.save(sesion);

        eventoPublisher.publicar(
                EventoSocket.of(
                        TipoEventoSocket.SESION_FINALIZADA,
                        sesionId,
                        null
                )
        );
    }
    // =========================
    // HELPERS
    // =========================
    private SesionIa obtenerSesion(String id) {
        return sesionRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Sesión no existe"));
    }

    private Pantalla obtenerPantalla(String id) {
        return pantallaRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Pantalla no existe"));
    }

    private void validarEstado(SesionIa sesion, EstadoSesion esperado) {
        if (sesion.getEstado() != esperado) {
            throw new IllegalStateException(
                    "Estado inválido. Esperado: " + esperado +
                            ", actual: " + sesion.getEstado()
            );
        }
    }

    /**
     * Obtiene la pantalla que está mostrando esta sesión.
     *
     * REQUIERE en PantallaIARepository:
     * Optional<Pantalla> findBySesionActualId(String sesionActualId);
     */
    private String obtenerPantallaIdPorSesion(String sesionId) {
        Pantalla pantalla = pantallaRepo.findBySesionActualId(sesionId)
                .orElseThrow(() -> new IllegalStateException("No hay pantalla asignada a la sesión: " + sesionId));
        return pantalla.getId();
    }
}
