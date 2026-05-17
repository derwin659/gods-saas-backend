package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.CreateDemoRequest;
import com.gods.saas.domain.dto.request.ReviewDemoRequest;
import com.gods.saas.domain.dto.response.DemoRequestResponse;

import java.util.List;

public interface DemoRequestService {

    DemoRequestResponse createPublicRequest(CreateDemoRequest request);

    List<DemoRequestResponse> findAll();

    List<DemoRequestResponse> findPending();

    DemoRequestResponse findById(Long id);

    DemoRequestResponse approve(Long id, ReviewDemoRequest request);

    DemoRequestResponse reject(Long id, ReviewDemoRequest request);

    DemoRequestResponse markSuspicious(Long id, ReviewDemoRequest request);
}