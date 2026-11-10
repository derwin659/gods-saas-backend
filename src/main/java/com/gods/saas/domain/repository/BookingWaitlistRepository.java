package com.gods.saas.domain.repository;
import com.gods.saas.domain.model.BookingWaitlistEntry;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface BookingWaitlistRepository extends JpaRepository<BookingWaitlistEntry,Long> {
 List<BookingWaitlistEntry> findTop100ByTenantIdAndCustomerIdOrderByCreatedAtDesc(Long tenantId,Long customerId);
 List<BookingWaitlistEntry> findTop200ByTenantIdAndBranchIdAndStatusInOrderByCreatedAtAsc(Long tenantId,Long branchId,List<String> statuses);
 List<BookingWaitlistEntry> findTop100ByStatusInOrderByCheckedAtAscCreatedAtAsc(List<String> statuses);
 List<BookingWaitlistEntry> findByTenantIdAndCustomerIdAndStatusIn(Long tenantId,Long customerId,List<String> statuses);
 long countByTenantIdAndCustomerIdAndStatusIn(Long tenantId,Long customerId,List<String> statuses);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select e from BookingWaitlistEntry e where e.id=:id")
 Optional<BookingWaitlistEntry> lockById(@Param("id") Long id);
}
