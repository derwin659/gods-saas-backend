package com.gods.saas.domain.model;

import com.gods.saas.domain.enums.SalaryFrequency;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(
        name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"tenant_id", "email"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    // ==============================
    // Tenant de la barbería
    // ==============================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    // ==============================
    // Sede (branch)
    // ==============================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @ToString.Exclude
    private Branch branch;

    @Column(length = 150)
    private String nombre;

    @Column(length = 150)
    private String apellido;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(length = 50)
    private String rol; // OWNER, ADMIN, BARBER, CASHIER, MANAGER

    @Column(name = "activo")
    private Boolean activo = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "photo_url")
    private String photoUrl;


    @Column(name = "salary_mode")
    private Boolean salaryMode = false;

    @Column(name = "commission_scheme", length = 30)
    private String commissionScheme;

    @Column(name = "commission_percentage", precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_frequency", length = 20)
    private SalaryFrequency salaryFrequency;

    @Column(name = "fixed_salary_amount", precision = 12, scale = 2)
    private BigDecimal fixedSalaryAmount;

    @Column(name = "salary_start_date")
    private LocalDate salaryStartDate;

    @Column(name = "photo_public_id", length = 255)
    private String photoPublicId;

    // ============================================================
    // SPRING SECURITY METHODS (IMPORTANTES)
    // ============================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security exige "ROLE_X"
        if (rol == null) return List.of();
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()));
    }

    @Override
    public String getPassword() {
        return this.passwordHash; // <-- LO QUE ESPERA BCrypt
    }

    @Override
    public String getUsername() {
        return this.email; // <-- Spring autenticará por email
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Puedes personalizar
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Puedes personalizar
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Puedes personalizar
    }

    @Override
    public boolean isEnabled() {
        return this.activo;
    }


}
