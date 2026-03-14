package com.gods.saas.service.impl;

import com.gods.saas.service.impl.impl.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final String UPLOAD_DIR = "uploads/users/";

    @Override
    public String uploadUserPhoto(MultipartFile file) {

        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));

            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get(UPLOAD_DIR + filename);

            Files.write(path, file.getBytes());

            // En prod esto sería S3 / Cloudinary
            return "/uploads/users/" + filename;

        } catch (IOException e) {
            throw new RuntimeException("Error al subir la imagen", e);
        }
    }
}
