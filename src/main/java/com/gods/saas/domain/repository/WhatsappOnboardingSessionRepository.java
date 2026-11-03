package com.gods.saas.domain.repository;
import com.gods.saas.domain.model.WhatsappOnboardingSession;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface WhatsappOnboardingSessionRepository extends JpaRepository<WhatsappOnboardingSession,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WhatsappOnboardingSession s where s.ticketHash=:hash")
    Optional<WhatsappOnboardingSession> findByTicketForUpdate(@Param("hash") String hash);
    Optional<WhatsappOnboardingSession> findByAccessHash(String hash);
}
