package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.model.*;

public interface LoyaltyService {

    void grantSalePoints(Tenant tenant, Customer customer, AppUser user, Sale sale, double total);

    void grantActivationBonusIfNeeded(Customer customer);

    void grantWelcomeBonusIfNeeded(Customer customer);

    int expirePoints();
}
