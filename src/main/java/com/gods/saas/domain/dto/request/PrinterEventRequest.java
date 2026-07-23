package com.gods.saas.domain.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrinterEventRequest {
    private String action;
    private Boolean success;
    private String printerName;
    private String message;
}
