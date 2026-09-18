# GODS Academy con Cloudinary

## Cómo subir un video

Después de desplegar esta versión, entrar con SUPER_ADMIN y abrir GODS Academy en el menú.

1. Elegir una lección o pulsar Nueva lección.
2. Completar título, funcionalidad, resumen, pasos y roles destinatarios. El permiso seleccionado solo limita a administradores; el servidor comprueba el permiso real de su sesión.
3. Seleccionar un MP4 de hasta 35 MB (preferible H.264, con subtítulos integrados). La subida guarda los datos y el video como borrador.
4. Esperar la confirmación de Cloudinary y revisar el video en la vista previa.
5. Pulsar Publicar. También se pueden publicar guías sin video.
6. En web o móvil, abrir Academy o pulsar Actualizar contenido.

Guardar borrador no cambia la publicación existente. Retirar publicación oculta la lección en la próxima consulta del catálogo, conservando su borrador. El catálogo y el progreso de lectura son distintos: el catálogo está en el backend; el progreso sigue siendo local por negocio, usuario y rol.

Web: solo dueño/administrador. Móvil: dueño/administrador, cliente, profesional y caja. El panel de gestión es exclusivo del superadministrador y requiere su autenticación real en el backend.

## Despliegue inicial obligatorio

1. Aplicar C:/godssass/docs/sql/20260917_gods_academy.sql en la base de datos del entorno. Hibernate está en ddl-auto=none. La migración crea la tabla e importa las doce guías; repetirla no reemplaza contenido editado.
2. Desplegar el backend con AcademyController, AcademyService y la integración de Cloudinary.
3. Desplegar la web y distribuir una actualización de la app que use /api/academy.
4. Probar una subida real con un MP4 de demostración y comprobar vista previa, publicación y retirada.

Las futuras lecciones y videos no necesitan una nueva compilación de la app. Los archivos JSON locales quedan como referencia inicial y datos para pruebas; no son el catálogo vivo.

## Cloudinary y archivos

Se reutilizan cloudinary.cloud-name, cloudinary.api-key y cloudinary.api-secret existentes. No agregar claves al frontend.
Carpeta: super-gods/academy/videos. Cada subida crea un recurso único: no sobrescribe el video que ya ven los usuarios.
Límite: 35 MB MP4. El backend valida tamaño, tipo, extensión y cabecera antes de subir. El límite multipart existente de 40 MB no cambia.
Los videos se entregan por URL HTTPS de Cloudinary; no son archivos con acceso firmado por usuario. Los tutoriales deben contener datos ficticios y contenido de formación, no información privada.
Los videos anteriores se conservan al reemplazar o retirar una lección. No eliminarlos mientras estén referenciados por draft_json o published_json. No hay limpieza automática de revisiones antiguas. Las nuevas subidas se eliminan si se confirma rollback de su transacción; si la subida pierde conexión antes de confirmar, revisar Cloudinary y la lección antes de reintentar.
La publicación controla qué lecciones muestra Academy; no invalida enlaces de videos previamente compartidos ni retira una lección ya abierta sin actualizar.

## API

GET /api/academy?platform=web|mobile: catálogo publicado, filtrado por rol y permisos en el servidor; Cache-Control: no-store.
GET/POST /api/super-admin/academy: listar borradores o crear lección.
PUT /api/super-admin/academy/{id}: guardar metadatos del borrador.
POST /api/super-admin/academy/{id}/video: multipart video + version.
POST /api/super-admin/academy/{id}/publish o /unpublish: JSON version.
Cada edición usa control de versión; si otra sesión cambió la lección, actualizar la lista y volver a seleccionarla antes de editar.

## Estado de entrega

Preparado en código; no se aplicó la migración en producción ni se hizo una subida real a Cloudinary durante esta implementación. Validar el despliegue antes de anunciar el panel disponible.