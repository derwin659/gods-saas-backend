# IA ilustrativa: modo RunPod Serverless

El backend mantiene dos modos compatibles:

- POD (predeterminado): usa FastAPI /generar y el encendido/apagado existente.
- SERVERLESS: usa POST https://api.runpod.ai/v2/{endpointId}/run y consulta GET /status/{jobId}.

## Variables

RUNPOD_MODE=POD|SERVERLESS
RUNPOD_API_KEY=...
RUNPOD_ENDPOINT_ID=...
RUNPOD_SERVERLESS_API_BASE_URL=https://api.runpod.ai/v2
RUNPOD_SERVERLESS_WAIT_MILLIS=600000
RUNPOD_SERVERLESS_EXECUTION_TIMEOUT_MILLIS=600000
RUNPOD_SERVERLESS_TTL_MILLIS=900000

No activar SERVERLESS hasta desplegar un worker compatible.

## Contrato requerido del worker

RunPod entrega al handler un objeto job con id e input. El contenido de input conserva el contrato GenerarImagenRequest: sesionId, imagenes (frontal, lateral y trasera), corte, tinte, ondulado y vistas. Además incluye storage.tenantId, obtenido del contexto autenticado y nunca del body del cliente.

El handler devuelve sesionId, imagenes con URLs autenticadas y assets con los publicId de Cloudinary. Requiere CLOUDINARY_URL y acepta entradas únicamente desde AI_ALLOWED_IMAGE_HOSTS.

El modelo debe cargarse fuera del handler para reutilizarlo entre solicitudes.

## Activación gradual

1. Aplicar docs/sql/20260901_ai_generation_jobs.sql.
2. Crear el endpoint Serverless con cero Active Workers y Flex Workers habilitados.
3. Probar el contenedor y el contrato desde la consola de RunPod.
4. Configurar RUNPOD_ENDPOINT_ID.
5. Cambiar RUNPOD_MODE=SERVERLESS en un entorno de prueba.
6. Confirmar jobs COMPLETED, tiempos y costo.
7. Mantener POD disponible para rollback.

## Manejo de trabajos y limpieza (14 de septiembre de 2026)

El backend consulta los estados IN_QUEUE e IN_PROGRESS hasta obtener COMPLETED,
FAILED, CANCELLED o TIMED_OUT, o hasta agotar SERVERLESS_WAIT_MILLIS.
Este límite incluye la espera en cola, no sólo la ejecución de GPU.

Las entradas se eliminan cuando se confirma un estado terminal. Si el envío o la
consulta pierde conexión y el estado no puede confirmarse, se conservan las
entradas. En el flujo de sesiones se registra REQUIRES_REVIEW junto al providerJobId
cuando está disponible. Los logs registran los identificadores de recursos
pendientes. No reenviar automáticamente una generación cuyo resultado se desconoce.

Antes de reintentar, revisar el trabajo en RunPod y recuperar el resultado o
confirmar que terminó. Después limpiar las entradas registradas. Sigue pendiente
automatizar esta reconciliación y limpieza de forma persistente tras reinicios.
El endpoint móvil síncrono aún puede agotar su tiempo de espera antes del backend;
falta conectar la app al seguimiento de trabajos para recuperación automática.

El worker ya sube resultados a Cloudinary y devuelve URLs autenticadas; no devuelve
las imágenes de salida en Base64. Si una vista falla, la generación se informa como
fallida y el handler elimina sus resultados parciales.

## Pendientes antes de producción

- Probar DockerfileHandler con CUDA, librerías del sistema y versiones reproducibles.
- Confirmar modelos y LoRA dentro del contenedor o volumen, sin rutas del Pod antiguo.
- Probar una generación real de tres vistas, cold start y memoria GPU.
- Aplicar y comprobar la migración SQL en un entorno de pruebas.
- Configurar endpoint y credenciales en RunPod/Railway, sin exponerlas al móvil.
- Automatizar reconciliación de REQUIRES_REVIEW y limpieza tras reinicios.
- Conectar seguimiento de trabajos desde móvil antes de activar para clientes.

No se ha activado SERVERLESS en producción ni se ha creado un endpoint durante
estas correcciones locales.