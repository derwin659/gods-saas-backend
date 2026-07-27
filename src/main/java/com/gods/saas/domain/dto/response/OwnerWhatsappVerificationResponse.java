package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OwnerWhatsappVerificationResponse {
    private String phone;
    private String maskedPhone;
    private Boolean verified;
    private LocalDateTime verifiedAt;
    private String pendingPhone;
    private String maskedPendingPhone;
    private LocalDateTime codeExpiresAt;
    private LocalDateTime canRequestAt;
    private Boolean verificationPending;
    private Boolean centralNotificationsEnabled;
    private String centralProvider;
    private String centralSenderLabel;
}
