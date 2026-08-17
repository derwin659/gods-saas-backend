package com.gods.saas.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final long MAX_VIDEO_SIZE_BYTES = 35L * 1024L * 1024L;
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/webm");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final Cloudinary cloudinary;

    public CloudinaryStorageService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret
    ) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    public UploadResult uploadAppointmentDepositEvidence(
            Long tenantId,
            Long customerId,
            MultipartFile file
    ) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/appointments/deposits";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "deposit_customer_" + customerId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir el comprobante del pago", e);
        }
    }

    public UploadResult uploadServiceImage(Long tenantId, Long serviceId, MultipartFile file) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/services";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "service_" + serviceId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la imagen a Cloudinary", e);
        }
    }

    public UploadResult uploadTenantLogo(Long tenantId, MultipartFile file) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/branding";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "tenant_logo_" + tenantId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir el logo del negocio a Cloudinary", e);
        }
    }

    public UploadResult uploadShowcaseImage(Long tenantId, Long professionalId, MultipartFile file) {
        validateImage(file);
        try {
            String folder = "super-gods/tenants/" + tenantId + "/showcase";
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "work_" + professionalId + "_" + System.currentTimeMillis(),
                            "overwrite", false,
                            "transformation", "c_limit,w_1800,h_1800,q_auto:good,f_auto"
                    )
            );
            return new UploadResult(String.valueOf(result.get("secure_url")), String.valueOf(result.get("public_id")));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la foto del trabajo", e);
        }
    }
    public UploadResult uploadShowcaseVideo(Long tenantId, Long professionalId, MultipartFile file) {
        validateVideo(file);
        try {
            String folder = "super-gods/tenants/" + tenantId + "/showcase/videos";
            Map<?, ?> result = cloudinary.uploader().uploadLarge(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "video",
                            "public_id", "work_video_" + professionalId + "_" + System.currentTimeMillis(),
                            "overwrite", false
                    )
            );
            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));
            validateUploadedVideoDuration(result, publicId);
            return new UploadResult(secureUrl, publicId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("No se pudo procesar el video. Inténtalo nuevamente.", e);
        }
    }

    public String videoThumbnailUrl(String secureVideoUrl) {
        if (secureVideoUrl == null || secureVideoUrl.isBlank()) return null;
        String transformed = secureVideoUrl.replace("/upload/", "/upload/so_0,c_fill,w_900,h_900,q_auto:good,f_jpg/");
        int extension = transformed.lastIndexOf('.');
        return extension > transformed.lastIndexOf('/') ? transformed.substring(0, extension) + ".jpg" : transformed + ".jpg";
    }
    public void deleteShowcaseMedia(String publicId, String mediaType) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            String resourceType = "VIDEO".equalsIgnoreCase(mediaType) ? "video" : "image";
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", resourceType, "invalidate", true)
            );
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo eliminar el archivo del trabajo", e);
        }
    }
    public void deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }

        try {
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", "image")
            );
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo eliminar la imagen de Cloudinary", e);
        }
    }

    private void validateVideo(MultipartFile file) {
        validateBasicFile(file, MAX_VIDEO_SIZE_BYTES, "El video es obligatorio", "El video no debe pesar más de 35 MB");
        String contentType = normalizedContentType(file);
        if (!ALLOWED_VIDEO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Formato no permitido. Usa MP4, MOV o WEBM");
        }
        byte[] header = readHeader(file, 16);
        boolean isoVideo = hasAscii(header, 4, "ftyp");
        boolean webm = startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
        boolean signatureMatches = switch (contentType) {
            case "video/mp4", "video/quicktime" -> isoVideo;
            case "video/webm" -> webm;
            default -> false;
        };
        if (!signatureMatches) {
            throw new IllegalArgumentException("El archivo no contiene un video válido o su formato fue alterado");
        }
        validateExtension(file, contentType, Map.of(
                "video/mp4", Set.of("mp4", "m4v"),
                "video/quicktime", Set.of("mov"),
                "video/webm", Set.of("webm")
        ));
    }

    private void validateImage(MultipartFile file) {
        validateBasicFile(file, MAX_FILE_SIZE_BYTES, "La imagen es obligatoria", "La imagen no debe pesar más de 5 MB");
        String contentType = normalizedContentType(file);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Formato no permitido. Usa JPG, PNG o WEBP");
        }
        byte[] header = readHeader(file, 16);
        boolean jpeg = startsWith(header, 0xFF, 0xD8, 0xFF);
        boolean png = startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
        boolean webp = hasAscii(header, 0, "RIFF") && hasAscii(header, 8, "WEBP");
        boolean signatureMatches = switch (contentType) {
            case "image/jpeg", "image/jpg" -> jpeg;
            case "image/png" -> png;
            case "image/webp" -> webp;
            default -> false;
        };
        if (!signatureMatches) {
            throw new IllegalArgumentException("El archivo no contiene una imagen válida o su formato fue alterado");
        }
        validateExtension(file, contentType, Map.of(
                "image/jpeg", Set.of("jpg", "jpeg"),
                "image/jpg", Set.of("jpg", "jpeg"),
                "image/png", Set.of("png"),
                "image/webp", Set.of("webp")
        ));
    }

    private void validateBasicFile(MultipartFile file, long maxBytes, String requiredMessage, String sizeMessage) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException(requiredMessage);
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(sizeMessage);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("El archivo no tiene un nombre válido");
        }
    }

    private String normalizedContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] readHeader(MultipartFile file, int length) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(length);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo. Selecciónalo nuevamente", e);
        }
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private boolean hasAscii(byte[] bytes, int offset, String expected) {
        if (offset < 0 || bytes.length < offset + expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) {
            if ((bytes[offset + i] & 0xFF) != expected.charAt(i)) return false;
        }
        return true;
    }

    private void validateExtension(MultipartFile file, String contentType, Map<String, Set<String>> allowed) {
        String filename = file.getOriginalFilename();
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowed.getOrDefault(contentType, Set.of()).contains(extension)) {
            throw new IllegalArgumentException("La extensión del archivo no coincide con su contenido");
        }
    }

    private void validateUploadedVideoDuration(Map<?, ?> result, String publicId) {
        Object rawDuration = result.get("duration");
        double duration;
        try {
            duration = rawDuration instanceof Number number
                    ? number.doubleValue()
                    : Double.parseDouble(String.valueOf(rawDuration));
        } catch (RuntimeException invalidDuration) {
            deleteInvalidUploadedVideo(publicId);
            throw new IllegalArgumentException("No se pudo verificar la duración real del video");
        }
        if (Double.isFinite(duration) && duration >= 1 && duration <= 90) return;
        deleteInvalidUploadedVideo(publicId);
        throw new IllegalArgumentException("El video debe durar entre 1 y 90 segundos");
    }

    private void deleteInvalidUploadedVideo(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "video", "invalidate", true));
        } catch (IOException cleanupError) {
            throw new IllegalStateException("El video no cumple las reglas y no pudo limpiarse", cleanupError);
        }
    }
    @Getter
    public static class UploadResult {
        private final String secureUrl;
        private final String publicId;

        public UploadResult(String secureUrl, String publicId) {
            this.secureUrl = secureUrl;
            this.publicId = publicId;
        }
    }

    public UploadResult uploadBranchImage(Long tenantId, Long branchId, MultipartFile file) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/branches";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "branch_" + branchId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la imagen de la sede a Cloudinary", e);
        }
    }

    public UploadResult uploadBarberPhoto(Long tenantId, Long barberId, MultipartFile file) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/barbers";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "barber_" + barberId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la foto del barbero a Cloudinary", e);
        }
    }

    public UploadResult uploadCustomerPhoto(Long tenantId, Long customerId, MultipartFile file) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/customers";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "customer_" + customerId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la foto del cliente a Cloudinary", e);
        }
    }



    public UploadResult uploadPromotionImage(Long tenantId, Long promotionId, MultipartFile file) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/promotions";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "promotion_" + promotionId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la imagen de la promoción a Cloudinary", e);
        }
    }

    public UploadResult uploadRewardImage(Long tenantId, Long rewardId, MultipartFile file) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/rewards";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "reward_" + rewardId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la imagen del premio a Cloudinary", e);
        }
    }



    public UploadResult uploadProductImage(Long tenantId, Long productId, MultipartFile file) {
        validateImage(file);

        try {
            String folder = "super-gods/tenants/" + tenantId + "/products";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "public_id", "product_" + productId + "_" + System.currentTimeMillis(),
                            "overwrite", true
                    )
            );

            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            return new UploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la imagen del producto a Cloudinary", e);
        }
    }

}
