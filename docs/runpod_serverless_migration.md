# IA ilustrativa: modo RunPod Serverless

El backend mantiene dos modos compatibles:

- POD (predeterminado): usa FastAPI /generar y el encendido/apagado existente.
- SERVERLESS: usa POST https://api.runpod.ai/v2/{endpointId}/runsync.

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

## Limitación temporal

Las fotos de entrada se suben como recursos `authenticated` separados por tenant y sesión. El payload conserva `imagenes.frontal`, `imagenes.lateral` e `imagenes.trasera`, pero en modo SERVERLESS esos valores son URLs firmadas en lugar de Base64. Los recursos se eliminan al terminar o fallar la llamada; POD continúa recibiendo Base64.

El `wait` predeterminado coincide con `executionTimeout` (10 minutos) para no limpiar entradas mientras el worker siga activo. No reducir `RUNPOD_SERVERLESS_WAIT_MILLIS` por debajo del tiempo máximo de ejecución sin implementar persistencia y limpieza diferida.

Sigue pendiente sacar las imágenes generadas del `output`: el worker final debe almacenarlas y devolver URLs para evitar que la respuesta alcance el límite de 20 MB.