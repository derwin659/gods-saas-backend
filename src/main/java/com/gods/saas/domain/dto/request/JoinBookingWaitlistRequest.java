package com.gods.saas.domain.dto.request;
import lombok.Data;
import java.time.*;
@Data public class JoinBookingWaitlistRequest {
 private Long branchId; private Long serviceId; private Long barberId;
 private LocalDate date; private LocalTime earliestTime; private LocalTime latestTime;
}
