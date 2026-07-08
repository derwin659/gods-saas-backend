package com.gods.saas.domain.dto;

import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import lombok.Data;

@Data
public class ClienteResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private String phone;
    private String email;
    private Long tenantId;
    private String origenCliente;
    private Integer puntosDisponibles;
    private String photoUrl;
    private Boolean whatsappTransactionalEnabled;
    private Boolean whatsappMarketingEnabled;
    private java.time.LocalDateTime whatsappOptedOutAt;
    private String customerNotes;
    private String preferredServices;
    private String customerRestrictions;
    private String preferredContactChannel;
    private String favoriteBarberName;

    public static ClienteResponse fromEntity(Customer u) {
        ClienteResponse r = new ClienteResponse();
        r.setId(u.getId());
        r.setNombre(u.getNombres());
        r.setApellido(u.getApellidos());
        r.setPhone(u.getTelefono());
        r.setEmail(u.getEmail());
        r.setTenantId(u.getTenant().getId());
        r.setOrigenCliente(u.getOrigenCliente());
        r.setPuntosDisponibles(u.getPuntosDisponibles() != null ? u.getPuntosDisponibles() : 0);
        r.setPhotoUrl(u.getPhotoUrl());
        r.setWhatsappTransactionalEnabled(u.getWhatsappTransactionalEnabled());
        r.setWhatsappMarketingEnabled(u.getWhatsappMarketingEnabled());
        r.setWhatsappOptedOutAt(u.getWhatsappOptedOutAt());
        r.setCustomerNotes(u.getCustomerNotes());
        r.setPreferredServices(u.getPreferredServices());
        r.setCustomerRestrictions(u.getCustomerRestrictions());
        r.setPreferredContactChannel(u.getPreferredContactChannel());
        r.setFavoriteBarberName(u.getFavoriteBarberName());
        return r;
    }

    public static ClienteResponse fromEntity(Customer u, Integer puntosDisponiblesReales) {
        ClienteResponse r = fromEntity(u);
        r.setPuntosDisponibles(puntosDisponiblesReales != null ? puntosDisponiblesReales : 0);
        return r;
    }
}

