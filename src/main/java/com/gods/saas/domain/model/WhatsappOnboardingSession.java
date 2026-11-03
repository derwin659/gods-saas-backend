package com.gods.saas.domain.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="whatsapp_onboarding_session") @Getter @Setter
public class WhatsappOnboardingSession {
    @Id private String id;
    @Column(nullable=false) private Long tenantId;
    @Column(nullable=false) private Long ownerId;
    @Column(nullable=false,length=20) private String phone;
    @JsonIgnore @Column(unique=true,nullable=false,length=64) private String ticketHash;
    @JsonIgnore @Column(unique=true,length=64) private String accessHash;
    @Column(nullable=false) private Instant ticketExpiresAt;
    @Column(nullable=false) private Instant expiresAt;
    @Version private Long version;
}
