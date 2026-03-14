package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.Subscription;

import org.springframework.data.jpa.repository.JpaRepository;


public interface SuscriptionRepository extends JpaRepository<Subscription, Long> {
}
