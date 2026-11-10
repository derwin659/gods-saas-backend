package com.gods.saas.domain.dto.response;
import java.time.*;
public record BookingWaitlistResponse(Long id,Long branchId,String branchName,Long serviceId,String serviceName,
 Long barberId,LocalDate date,LocalTime earliestTime,LocalTime latestTime,String status,
 LocalTime offeredTime,Instant offerExpiresAt,Long customerId,String customerName) {}
