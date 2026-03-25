package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Entity
@Table(name = "user_tenant_roles")
@Data
@AllArgsConstructor
@Builder
public class UserTenantRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    private RoleType role;



    public UserTenantRole() {
    }

    public UserTenantRole(AppUser user, Tenant tenant, RoleType role) {
        this.user = user;
        this.tenant = tenant;
        this.role = role;
    }

    public UserTenantRole(AppUser user, Tenant tenant, Branch branch, RoleType role) {
        this.user = user;
        this.tenant = tenant;
        this.branch = branch;
        this.role = role;
    }


}

