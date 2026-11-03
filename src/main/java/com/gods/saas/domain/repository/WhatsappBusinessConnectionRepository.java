package com.gods.saas.domain.repository;
import com.gods.saas.domain.model.WhatsappBusinessConnection;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface WhatsappBusinessConnectionRepository extends JpaRepository<WhatsappBusinessConnection,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WhatsappBusinessConnection c where c.tenantId=:tenant")
    Optional<WhatsappBusinessConnection> findForUpdate(@Param("tenant") Long tenant);
    List<WhatsappBusinessConnection> findByState(String state);
}
