package com.gods.saas.domain.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="whatsapp_business_connection") @Getter @Setter
public class WhatsappBusinessConnection {
    @Id private Long tenantId;
    @Column(unique=true,nullable=false,length=20) private String phone;
    @Column(unique=true,length=34) private String accountSid;
    @JsonIgnore @Column(length=512) private String encryptedAuthToken;
    @Column(unique=true,length=40) private String wabaId;
    @Column(unique=true,length=34) private String senderSid;
    @Column(nullable=false,length=32) private String state="RESERVED";
    @Column(length=64) private String errorCode;
    private String bookingContentSid;
    private String depositContentSid;
    private String saleReceiptContentSid;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition="jsonb")
    private java.util.Map<String,String> saleReceiptContentSids;
    private String reminder60ContentSid;
    private Instant updatedAt=Instant.now();
    @Version private Long version;
}
