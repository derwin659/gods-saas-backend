package com.gods.saas.service.impl;

import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ============================================================
    //  GENERAR TOKEN PARA CLIENTES (LOGIN POR OTP)
    // ============================================================
    public String generateCustomerToken(Customer customer) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "customer");
        claims.put("tenantId", customer.getTenant().getId()); // ✅ NUEVO
        claims.put("customerId", customer.getId());
        claims.put("phone", customer.getTelefono());
        claims.put("role", "CLIENT"); // ✅ opcional pero útil

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("customer-" + customer.getId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    // ============================================================
    //  GENERAR TOKEN PARA USUARIOS INTERNOS (ADMIN, BARBER, OWNER)
    // ============================================================
    public String generateInternalUserToken(AppUser user) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "internal");
        claims.put("userId", user.getId());
        claims.put("tenantId", user.getTenant().getId());
        claims.put("branchId", user.getBranch() != null ? user.getBranch().getId() : null);
        claims.put("role", user.getRol()); // admin, barber, owner

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("internal-" + user.getId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 2592000000L))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ============================================================
    //  VALIDA Y OBTIENE CLAIMS
    // ============================================================
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    // Getters opcionales
    public String getUserType(String token) {
        return extractAllClaims(token).get("userType", String.class);
    }

    public Long getInternalUserId(String token) {
        return extractAllClaims(token).get("userId", Number.class).longValue();
    }

    public Long getCustomerId(String token) {
        return extractAllClaims(token).get("customerId", Number.class).longValue();
    }


    public String generateToken(AppUser user, Long tenantId, String role, Long branchId) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "internal");
        claims.put("userId", user.getId());
        claims.put("tenantId", tenantId);
        claims.put("role", role);
        claims.put("branchId", branchId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }


    public String generateFinalLoginToken(AppUser user, Long tenantId, String role) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "internal");
        claims.put("userId", user.getId());
        claims.put("tenantId", tenantId);
        claims.put("role", role);  // OWNER, MANAGER, BARBER
        claims.put("email", user.getEmail());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("internal-" + user.getId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateSuperAdminToken(AppUser user) {
        if (user == null) {
            throw new IllegalArgumentException("Usuario no puede ser null");
        }

        if (user.getRol() == null || !"SUPER_ADMIN".equalsIgnoreCase(user.getRol())) {
            throw new IllegalArgumentException("El usuario no tiene rol SUPER_ADMIN");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "internal");
        claims.put("userId", user.getId());
        claims.put("role", user.getRol());
        claims.put("email", user.getEmail());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}
