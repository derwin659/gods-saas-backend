package com.gods.saas.domain.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="booking_waitlist") @Getter @Setter @NoArgsConstructor
public class BookingWaitlistEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long tenantId;
 @Column(nullable=false) private Long customerId;
 @Column(nullable=false) private Long branchId;
 @Column(nullable=false) private Long serviceId;
 private Long barberId;
 private LocalDate requestedDate;
 private LocalTime earliestTime;
 private LocalTime latestTime;
 private String status;
 private LocalTime offeredTime;
 private Instant offerExpiresAt;
 private Instant checkedAt;
 private Instant createdAt;
 private Instant updatedAt;
 @Version private long version;
}
