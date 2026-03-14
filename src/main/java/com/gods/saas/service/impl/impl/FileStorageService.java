package com.gods.saas.service.impl.impl;


import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String uploadUserPhoto(MultipartFile file);

}

