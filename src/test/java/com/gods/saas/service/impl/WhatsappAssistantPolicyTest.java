package com.gods.saas.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class WhatsappAssistantPolicyTest {
    @Test void assistantIncludesProLegacyAndKeepsOtherUnsupportedPlansDisabled() {
        var policy = new WhatsappAssistantPolicy();
        ReflectionTestUtils.setField(policy,"starterTurns",100);
        ReflectionTestUtils.setField(policy,"growthTurns",300);
        ReflectionTestUtils.setField(policy,"proTurns",600);
        assertEquals(100,policy.monthlyTurns("STARTER"));
        assertEquals(300,policy.monthlyTurns("GROWTH"));
        assertEquals(600,policy.monthlyTurns("PRO"));
        assertEquals(600,policy.monthlyTurns("PRO_LEGACY"));
        ReflectionTestUtils.setField(policy,"proTurns",250);
        assertEquals(250,policy.monthlyTurns("PRO_LEGACY"));
        ReflectionTestUtils.setField(policy,"proTurns",600);
        assertEquals(600,policy.monthlyTurns("ENTERPRISE"));
        for (String plan : new String[]{"FREE","BASIC","STARTER_LEGACY","unknown"}) assertEquals(0,policy.monthlyTurns(plan));
        assertEquals(0,policy.monthlyTurns(null));
        ReflectionTestUtils.setField(policy,"starterTurns",-1);
        assertEquals(0,policy.monthlyTurns("STARTER"));
    }
}
