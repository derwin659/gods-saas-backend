package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.ReportPaymentRequest;
import com.gods.saas.domain.dto.response.SubscriptionCurrentResponse;
import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.model.SubscriptionPayment;

public interface SubscriptionService {

    Subscription getCurrentSubscriptionOrThrow(Long tenantId);

    void validateSubscriptionActive(Long tenantId);

    void validateBranchLimit(Long tenantId);

    void validateBarberLimit(Long tenantId);

    void validateAdminLimit(Long tenantId);

    Subscription createStarterTrial(Long tenantId);

    Subscription changePlan(Long tenantId, String plan);

    SubscriptionCurrentResponse getCurrentSubscriptionResponse(Long tenantId);

    SubscriptionPayment reportManualPayment(Long tenantId, ReportPaymentRequest request);

    Subscription approveManualPayment(Long paymentId, Long reviewedByUserId);



    SubscriptionPayment rejectManualPayment(Long paymentId, Long reviewedByUserId, String reason);
}
