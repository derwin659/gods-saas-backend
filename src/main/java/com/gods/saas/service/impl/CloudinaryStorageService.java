package com.gods.saas.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
                            "overwrite", false,
                            "eager", "c_limit,w_1280,h_1280,q_auto:good"
                    )
            );
            return new UploadResult(String.valueOf(result.get("secure_url")), String.valueOf(result.get("public_id")));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir el video del trabajo", e);
        }
    }

    public String videoThumbnailUrl(String secureVideoUrl) {
        if (secureVideoUrl == null || secureVideoUrl.isBlank()) return null;
        String transformed = secureVideoUrl.replace("/upload/", "/upload/so_0,c_fill,w_900,h_900,q_auto:good,f_jpg/");
        int extension = transformed.lastIndexOf('.');
        return extension > transformed.lastIndexOf('/') ? transformed.substring(0, extension) + ".jpg" : transformed + ".jpg";
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
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("El video es obligatorio");
        if (file.getSize() > MAX_VIDEO_SIZE_BYTES) throw new IllegalArgumentException("El video no debe pesar mas de 35 MB");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Formato no permitido. Usa MP4, MOV o WEBM");
        }
    }
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("La imagen es obligatoria");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("La imagen no debe pesar más de 5 MB");
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Formato no permitido. Usa JPG, PNG o WEBP");
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
