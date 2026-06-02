package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class UpdateWhatsappSettingsRequest {
    private Boolean postSaleMessageEnabled;
    private Boolean includeAppDownloadLink;
    private Boolean includeBookingLink;
    private Boolean appointmentReminder60Enabled;
    private Boolean appointmentReminder24hEnabled;
    private Boolean inactiveCustomerFollowUpEnabled;
    private String appDownloadUrl;
    private String provider;
    private String connectionStatus;
    private String senderPhone;
    private String senderLabel;
}
