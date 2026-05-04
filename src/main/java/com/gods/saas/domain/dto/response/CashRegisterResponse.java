package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CashRegisterResponse {
    private Long id;
    private String status;
    private Long branchId;
    private String branchName;
    private Long openedByUserId;
    private String openedByUserName;
    private Long assignedUserId;
    private String assignedUserName;
    private BigDecimal openingAmount;
    private BigDecimal closingAmountExpected;
    private BigDecimal closingAmountCounted;
    private BigDecimal differenceAmount;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private String openingNote;
    private String closingNote;
    private BigDecimal salesTotal;
    private BigDecimal cashSalesTotal;
    private BigDecimal movementsIncome;
    private BigDecimal movementsExpense;
    private BigDecimal movementsExpenseGeneral;
    private BigDecimal movementsAdvanceBarber;
    private BigDecimal movementsPaymentBarber;
    private List<PaymentMethodSummaryResponse> paymentMethodsSummary;
    private List<CashMovementResponse> movements;
}
