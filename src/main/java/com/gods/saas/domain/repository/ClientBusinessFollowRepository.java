package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.ClientBusinessFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClientBusinessFollowRepository extends JpaRepository<ClientBusinessFollow, Long> {
    List<ClientBusinessFollow> findByFollowerPhoneOrderByCreatedAtDesc(String followerPhone);
    Optional<ClientBusinessFollow> findByFollowerPhoneAndTenant_Id(String followerPhone, Long tenantId);
    boolean existsByFollowerPhoneAndTenant_Id(String followerPhone, Long tenantId);
    long countByTenant_Id(Long tenantId);
    void deleteByFollowerPhoneAndTenant_Id(String followerPhone, Long tenantId);
}
