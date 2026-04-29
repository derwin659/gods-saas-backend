package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Usuario que registró la venta en el sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    /** Caja abierta donde se registró la venta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    @Column(name = "subtotal", precision = 12, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount", precision = 12, scale = 2, nullable = false)
    private BigDecimal discount;

    /** Propina del cliente. No forma parte del precio del servicio/producto. */
    @Column(name = "tip_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal tipAmount = BigDecimal.ZERO;

    /** Barbero que recibirá la propina. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tip_barber_user_id")
    private AppUser tipBarberUser;

    /** Total cobrado = subtotal + propina - descuento. */
    @Column(name = "total", precision = 12, scale = 2, nullable = false)
    private BigDecimal total;

    /** Método principal. Si hay pagos mixtos se guarda MIXED. */
    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    /** Fecha/hora real en la que ocurrió la venta. */
    @Column(name = "sale_date")
    private LocalDateTime saleDate;

    /** Fecha/hora en la que la venta fue registrada en el sistema. */
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "cash_received", precision = 12, scale = 2)
    private BigDecimal cashReceived;

    @Column(name = "change_amount", precision = 12, scale = 2)
    private BigDecimal changeAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalePayment> payments = new ArrayList<>();

    public void addPayment(SalePayment payment) {
        if (payments == null) {
            payments = new ArrayList<>();
        }
        payment.setSale(this);
        payments.add(payment);
    }
}
