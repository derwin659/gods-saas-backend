package com.gods.saas.domain.repository;

import com.gods.saas.domain.enums.DemoRequestStatus;
import com.gods.saas.domain.model.DemoRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DemoRequestRepository extends JpaRepository<DemoRequest, Long> {

    List<DemoRequest> findByStatusOrderByCreatedAtDesc(DemoRequestStatus status);

    List<DemoRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByOwnerEmailIgnoreCaseAndStatus(
            String ownerEmail,
            DemoRequestStatus status
    );

    boolean existsByOwnerPhoneAndStatus(
            String ownerPhone,
            DemoRequestStatus status
    );
}