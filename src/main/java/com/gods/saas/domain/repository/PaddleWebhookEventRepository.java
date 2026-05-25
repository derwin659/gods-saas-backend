package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.PaddleWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaddleWebhookEventRepository extends JpaRepository<PaddleWebhookEvent, Long> {
    Optional<PaddleWebhookEvent> findByEventId(String eventId);
}
