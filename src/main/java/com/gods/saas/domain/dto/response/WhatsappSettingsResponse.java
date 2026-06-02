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
    private String appDownloadUrl;
}
