package com.gods.saas.utils;

public enum EstadoPantalla {

    LIBRE,               // No muestra sesión
    MOSTRANDO_SESION,    // Mostrando recomendaciones
    ESPERANDO_DECISION,  // Cliente eligiendo
    GENERANDO_IMAGEN,    // IA generativa trabajando
    BLOQUEADA            // Error / mantenimiento

}

