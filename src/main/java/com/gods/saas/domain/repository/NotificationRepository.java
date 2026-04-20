package com.gods.saas.domain.repository;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByTenant_IdAndCustomer_IdOrderByCreatedAtDesc(Long tenantId, Long customerId);

    List<Notification> findByTenant_IdAndUser_IdOrderByCreatedAtDesc(Long tenantId, Long userId);

    long countByTenant_IdAndCustomer_IdAndIsReadFalse(Long tenantId, Long customerId);

    long countByTenant_IdAndUser_IdAndIsReadFalse(Long tenantId, Long userId);

    boolean existsByTypeAndReferenceTypeAndReferenceId(
            NotificationType type,
            String referenceType,
            Long referenceId
    );
    Optional<Notification> findByIdAndTenant_IdAndCustomer_Id(Long id, Long tenantId, Long customerId);

    Optional<Notification> findByIdAndTenant_IdAndUser_Id(Long id, Long tenantId, Long userId);

    List<Notification> findByTenant_IdAndUser_IdAndIsReadFalse(Long tenantId, Long userId);

    List<Notification> findByTenant_IdAndCustomer_IdAndIsReadFalse(Long tenantId, Long customerId);
}