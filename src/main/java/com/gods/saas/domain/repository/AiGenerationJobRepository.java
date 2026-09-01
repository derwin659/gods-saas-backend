package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.AiGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiGenerationJobRepository extends JpaRepository<AiGenerationJob, String> {
    List<AiGenerationJob> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}