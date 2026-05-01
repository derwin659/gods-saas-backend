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


}