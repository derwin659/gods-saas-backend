package com.gods.saas.service.impl;
import com.gods.saas.domain.repository.WhatsappBusinessConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class WhatsappBusinessConnectionStore {
    private final WhatsappBusinessConnectionRepository repository;
    private final WhatsappCredentialCipher cipher;
    public List<WhatsappBusinessSenderService.Connection> active() {
        List<WhatsappBusinessSenderService.Connection> result=new ArrayList<>();
        for (var row:repository.findByState("ONLINE")) {
            try {
                result.add(new WhatsappBusinessSenderService.Connection(row.getTenantId(),row.getPhone(),row.getAccountSid(),
                    cipher.decrypt(row.getTenantId(),row.getAccountSid(),row.getEncryptedAuthToken()),row.getBookingContentSid(),row.getDepositContentSid(),row.getSaleReceiptContentSid(),row.getReminder60ContentSid(),row.getSaleReceiptContentSids()));
            } catch (IllegalStateException invalid) { /* A failed decryption never selects another business. */ }
        }
        return result;
    }
}
