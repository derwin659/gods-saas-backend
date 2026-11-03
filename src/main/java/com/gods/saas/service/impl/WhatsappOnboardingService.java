package com.gods.saas.service.impl;

import com.gods.saas.config.WhatsappOnboardingProperties;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class WhatsappOnboardingService {
    private final WhatsappOnboardingProperties properties;
    private final WhatsappCredentialCipher cipher;
    private final WhatsappOnboardingProvider provider;
    private final WhatsappBusinessSenderService senders;
    private final WhatsappBusinessConnectionRepository connections;
    private final WhatsappOnboardingSessionRepository sessions;
    private final TenantSettingsRepository settings;
    private final UserTenantRoleRepository roles;
    private final TransactionTemplate tx;
    private final SecureRandom random=new SecureRandom();
    public WhatsappOnboardingService(WhatsappOnboardingProperties properties,WhatsappCredentialCipher cipher,
            WhatsappOnboardingProvider provider,WhatsappBusinessSenderService senders,WhatsappBusinessConnectionRepository connections,
            WhatsappOnboardingSessionRepository sessions,TenantSettingsRepository settings,UserTenantRoleRepository roles,PlatformTransactionManager manager) {
        this.properties=properties;this.cipher=cipher;this.provider=provider;this.senders=senders;this.connections=connections;
        this.sessions=sessions;this.settings=settings;this.roles=roles;this.tx=new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Map<String,Object> information(Long tenant,Long owner) {
        requireOwner(tenant,owner);
        boolean available=properties.configured() && cipher.ready() && provider.ready();
        Map<String,Object> result=new LinkedHashMap<>(status(connections.findById(tenant).orElse(null)));
        result.put("available",available);
        result.put("availabilityMessage",available ? "Autoriza la conexión directamente con Meta." : "La conexión guiada aún no está habilitada. Contacta a soporte de SuperGods.");
        return result;
    }
    public Map<String,Object> start(Long tenant,Long owner,String rawPhone) {
        requireReady();requireOwner(tenant,owner);
        String phone=WhatsappBusinessSenderService.normalizePhone(rawPhone);
        if(phone==null || senders.isSharedDestination(phone))throw new IllegalArgumentException("Ingresa el WhatsApp propio del negocio con código de país.");
        if(senders.connection(tenant).isPresent())throw new IllegalArgumentException("Este negocio ya tiene un número conectado. Actualiza su estado antes de iniciar otra conexión.");
        return tx.execute(transaction -> {
            var tenantSettings=settings.findForWhatsappUpdate(tenant).orElseThrow(()->new IllegalArgumentException("Negocio no encontrado."));
            var row=connections.findForUpdate(tenant).orElse(null);
            if(row!=null && !phone.equals(row.getPhone())) {
                if("RESERVED".equals(row.getState()) && row.getAccountSid()==null && row.getWabaId()==null && row.getSenderSid()==null)row.setPhone(phone);
                else throw new IllegalArgumentException("Ya existe una conexión en curso con otro número. Contacta a soporte para cambiarlo.");
            }
            if(row!=null && Set.of("PROVISIONING","REGISTERING","REVIEW_REQUIRED").contains(row.getState()))
                throw new IllegalArgumentException("La conexión está en proceso o requiere revisión. Comprueba su estado; no repitas el alta.");
            if(row==null) {row=new WhatsappBusinessConnection();row.setTenantId(tenant);row.setPhone(phone);}
            if(row.getUpdatedAt()!=null && row.getVersion()!=null && row.getUpdatedAt().plusSeconds(10).isAfter(Instant.now()))
                throw new IllegalArgumentException("Espera unos segundos antes de abrir otra conexión.");
            row.setUpdatedAt(Instant.now());connections.saveAndFlush(row);
            Map<String,Object> config=new HashMap<>(tenantSettings.getScheduleConfig()==null?Map.of():tenantSettings.getScheduleConfig());
            config.put(WhatsappBusinessSenderService.MODE_KEY,"BUSINESS");config.put(WhatsappBusinessSenderService.PHONE_KEY,phone);
            tenantSettings.setScheduleConfig(config);settings.save(tenantSettings);
            String ticket=token();var session=new WhatsappOnboardingSession();session.setId(UUID.randomUUID().toString());
            session.setTenantId(tenant);session.setOwnerId(owner);session.setPhone(phone);session.setTicketHash(hash(ticket));
            session.setTicketExpiresAt(Instant.now().plusSeconds(300));session.setExpiresAt(Instant.now().plusSeconds(1800));sessions.saveAndFlush(session);
            return Map.of("launchUrl",properties.getWebUrl().replaceAll("/+$","")+"/whatsapp/connect#ticket="+ticket,"ticket",ticket,"expiresAt",session.getTicketExpiresAt());
        });
    }
    public Map<String,Object> claim(String ticket) {
        requireReady();validateToken(ticket);
        return tx.execute(transaction -> {
            var session=sessions.findByTicketForUpdate(hash(ticket)).orElseThrow(this::expired);
            if(session.getAccessHash()!=null || session.getTicketExpiresAt().isBefore(Instant.now()))throw expired();
            requireOwner(session.getTenantId(),session.getOwnerId());
            var connection=connections.findById(session.getTenantId()).orElseThrow(this::expired);
            if(!session.getPhone().equals(connection.getPhone()))throw expired();
            String access=token();session.setAccessHash(hash(access));sessions.saveAndFlush(session);
            return Map.of("sessionToken",access,"phone",session.getPhone(),"appId",properties.getAppId(),"configId",properties.getConfigId(),
                    "solutionId",properties.getSolutionId(),"graphVersion",properties.getGraphVersion(),"expiresAt",session.getExpiresAt());
        });
    }
    public Map<String,Object> sessionStatus(String access) {
        var session=authorize(access);return refresh(session.getTenantId());
    }
    public Map<String,Object> refreshForOwner(Long tenant,Long owner) {requireOwner(tenant,owner);return refresh(tenant);}
    public Map<String,Object> complete(String access,String code,String waba,String phoneId) {
        requireReady();var session=authorize(access);
        if(code==null || code.isBlank() || code.length()>4096 || !numeric(waba) || !numeric(phoneId))
            throw new IllegalArgumentException("Meta no devolvió una autorización válida. Vuelve a conectar tu WhatsApp.");
        var existing=connections.findById(session.getTenantId()).orElseThrow(this::expired);
        if(existing.getSenderSid()!=null)return refresh(session.getTenantId());
        if(Set.of("PROVISIONING","REGISTERING","REVIEW_REQUIRED").contains(existing.getState()))return status(existing);
        try {provider.verifyAuthorization(code,waba,phoneId,session.getPhone());}
        catch(WhatsappOnboardingProvider.Failure invalid){throw new IllegalArgumentException("No se pudo verificar la autorización del número en Meta. Vuelve a intentarlo desde la ventana oficial.");}
        // Commit the intent BEFORE external creation. A crash/timeout must never trigger a blind second creation.
        var claimed=tx.execute(transaction -> {
            var row=connections.findForUpdate(session.getTenantId()).orElseThrow(this::expired);
            requireOwner(session.getTenantId(),session.getOwnerId());
            if(!row.getPhone().equals(session.getPhone()))throw expired();
            if(Set.of("PROVISIONING","REGISTERING","REVIEW_REQUIRED","ONLINE").contains(row.getState()))return null;
            if(row.getWabaId()!=null && !row.getWabaId().equals(waba))throw new IllegalArgumentException("Esta conexión ya está vinculada a otro negocio de Meta.");
            row.setWabaId(waba);row.setState(row.getAccountSid()==null?"PROVISIONING":"REGISTERING");
            row.setErrorCode(null);row.setUpdatedAt(Instant.now());connections.saveAndFlush(row);return row;
        });
        if(claimed==null)return status(connections.findById(session.getTenantId()).orElseThrow(this::expired));
        try {
            WhatsappOnboardingProvider.Account account;
            if(claimed.getAccountSid()==null) {
                account=provider.createAccount(session.getTenantId());
                tx.executeWithoutResult(transaction -> {
                    var row=connections.findForUpdate(session.getTenantId()).orElseThrow(this::expired);
                    row.setAccountSid(account.sid());row.setEncryptedAuthToken(cipher.encrypt(row.getTenantId(),account.sid(),account.token()));
                    row.setState("REGISTERING");row.setUpdatedAt(Instant.now());connections.saveAndFlush(row);
                });
            } else account=new WhatsappOnboardingProvider.Account(claimed.getAccountSid(),cipher.decrypt(claimed.getTenantId(),claimed.getAccountSid(),claimed.getEncryptedAuthToken()));
            var sender=provider.register(account,session.getPhone(),waba);
            return persistSender(session.getTenantId(),sender);
        } catch(WhatsappOnboardingProvider.Failure failure) {
            return failed(session.getTenantId(),failure.uncertain()?"REVIEW_REQUIRED":"ERROR","PROVIDER_FAILURE");
        } catch(RuntimeException failure) {
            return failed(session.getTenantId(),"REVIEW_REQUIRED","LOCAL_FAILURE_AFTER_INTENT");
        }
    }
    private Map<String,Object> refresh(Long tenant) {
        var row=connections.findById(tenant).orElse(null);
        if(row==null || row.getSenderSid()==null || !Set.of("PENDING","ONLINE","OFFLINE").contains(row.getState()))return status(row);
        if(row.getUpdatedAt()!=null && row.getUpdatedAt().plusSeconds(5).isAfter(Instant.now()))return status(row);
        try {
            var account=new WhatsappOnboardingProvider.Account(row.getAccountSid(),cipher.decrypt(tenant,row.getAccountSid(),row.getEncryptedAuthToken()));
            return persistSender(tenant,provider.refresh(account,row.getSenderSid()));
        } catch(RuntimeException failure) {return status(row);}
    }
    private Map<String,Object> persistSender(Long tenant,WhatsappOnboardingProvider.Sender sender) {
        return tx.execute(transaction -> {
            var row=connections.findForUpdate(tenant).orElseThrow(this::expired);
            if(sender==null || !row.getPhone().equals(sender.phone()) || !Objects.equals(row.getWabaId(),sender.waba())
                    || sender.sid()==null || !sender.sid().matches("XE[0-9a-fA-F]{32}")
                    || row.getSenderSid()!=null && !row.getSenderSid().equals(sender.sid())) {
                row.setState("REVIEW_REQUIRED");row.setErrorCode("SENDER_MISMATCH");
            } else {
                row.setSenderSid(sender.sid());
                row.setState("ONLINE".equals(sender.status())?"ONLINE":"OFFLINE".equals(sender.status())?"OFFLINE":"PENDING");
                row.setErrorCode(null);
            }
            row.setUpdatedAt(Instant.now());connections.saveAndFlush(row);return status(row);
        });
    }
    private Map<String,Object> failed(Long tenant,String state,String code) {
        return tx.execute(transaction -> {
            var row=connections.findForUpdate(tenant).orElseThrow(this::expired);row.setState(state);row.setErrorCode(code);row.setUpdatedAt(Instant.now());connections.saveAndFlush(row);return status(row);
        });
    }
    private Map<String,Object> status(WhatsappBusinessConnection row) {
        String state=row==null?"NOT_STARTED":row.getState();
        String message=switch(state) {
            case "ONLINE" -> "WhatsApp conectado. Ya puedes compartir tu enlace de reservas.";
            case "RESERVED","NOT_STARTED" -> "Autoriza la conexión con Meta para continuar.";
            case "REVIEW_REQUIRED" -> "La conexión necesita revisión de soporte. No repitas el alta.";
            case "ERROR" -> "El proveedor no completó el registro. Puedes volver a autorizar en Meta.";
            case "OFFLINE" -> "El número está registrado, pero aún no está disponible. Comprueba su estado más tarde.";
            default -> "La conexión está en proceso. Comprueba su estado en unos momentos.";
        };
        return Map.of("state",state,"connected","ONLINE".equals(state),"message",message,"phone",row==null?"":row.getPhone());
    }
    private WhatsappOnboardingSession authorize(String access) {
        validateToken(access);var session=sessions.findByAccessHash(hash(access)).orElseThrow(this::expired);
        if(!session.getExpiresAt().isAfter(Instant.now()))throw expired();
        var connection=connections.findById(session.getTenantId()).orElseThrow(this::expired);
        if(!session.getPhone().equals(connection.getPhone()))throw expired();
        requireOwner(session.getTenantId(),session.getOwnerId());return session;
    }
    private void requireOwner(Long tenant,Long owner) {
        if(tenant==null || owner==null || !roles.existsByUserIdAndTenantIdAndRoleIn(owner,tenant,List.of(RoleType.OWNER)))
            throw new IllegalArgumentException("Solo el dueño del negocio puede conectar su WhatsApp.");
    }
    private void requireReady(){if(!properties.configured() || !cipher.ready() || !provider.ready())throw new IllegalArgumentException("La conexión guiada aún no está habilitada. Contacta a soporte de SuperGods.");}
    private boolean numeric(String value){return value!=null && value.matches("[0-9]{5,30}");}
    private void validateToken(String value){if(value==null || !value.matches("[A-Za-z0-9_-]{43}"))throw expired();}
    private IllegalArgumentException expired(){return new IllegalArgumentException("Este enlace venció o ya fue utilizado. Abre una nueva conexión desde SuperGods.");}
    private String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static String hash(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception bad){throw new IllegalStateException("No se pudo crear el enlace seguro.");}}
}
