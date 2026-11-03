package com.gods.saas.service.impl;
public interface WhatsappOnboardingProvider {
    record Account(String sid,String token) { @Override public String toString(){return "Subaccount [redacted]";} }
    record Sender(String sid,String status,String phone,String waba) {}
    class Failure extends RuntimeException {
        private final boolean uncertain;
        public Failure(boolean uncertain){super("No se pudo completar la conexión con el proveedor.");this.uncertain=uncertain;}
        public boolean uncertain(){return uncertain;}
    }
    boolean ready();
    void verifyAuthorization(String code,String waba,String phoneId,String phone);
    Account createAccount(Long tenantId);
    Sender register(Account account,String phone,String waba);
    Sender refresh(Account account,String senderSid);
}
