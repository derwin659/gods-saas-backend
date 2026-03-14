package com.gods.saas.utils;

public enum EstadoSesion {

    CREADA,               // Barbero creó la sesión
    EN_COLA,              // Esperando turno para la TV
    MOSTRANDO_EN_TV,      // Visible en pantalla
    SELECCIONADA,         // Cliente eligió corte/tinte
    GENERANDO_IMAGEN,     // IA generativa en proceso
    FINALIZADA,           // Flujo completo
    CANCELADA,            // Barbero canceló
    EXPIRADA,
    IMAGEN_GENERADA// Timeout / abandono

}

