package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsappSettingsResponse {
    private Boolean postSaleMessageEnabled;
    private Boolean includeAppDownloadLink;
    private Boolean includeBookingLink;
    private Boolean appointmentReminder60Enabled;
    private Boolean appointmentReminder24hEnabled;
    private Boolean inactiveCustomerFollowUpEnabled;
    private Boolean ownerBookingAlertEnabled;
    private Boolean ownerBookingAlertIncludeAdmins;
    private Boolean ownerBookingAlertIncludeProfessional;
    private Boolean ownerBookingAlertIncludeStaffCreated;
    private String appDownloadUrl;
    private String senderMode;
    private String businessPhone;
    private String provider;
    private String connectionStatus;
    private String senderPhone;
    private String senderLabel;
    private String businessConnectionStatus;
    private String effectiveSenderMode;
    private String effectiveSenderPhone;
    private Boolean connected;
    private Boolean centralNotificationsEnabled;
    private String centralProvider;
    private String centralSenderLabel;
}
