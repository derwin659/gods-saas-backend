package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "marketing_campaign")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "marketing_campaign_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "channel_push", nullable = false)
    private boolean channelPush;

    @Column(name = "channel_in_app", nullable = false)
    private boolean channelInApp;

    @Column(name = "channel_whatsapp", nullable = false)
    private boolean channelWhatsapp;

    @Column(name = "custom_title", length = 150)
    private String customTitle;

    @Column(name = "custom_message", length = 500)
    private String customMessage;

    @Column(name = "days_inactive")
    private Integer daysInactive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}