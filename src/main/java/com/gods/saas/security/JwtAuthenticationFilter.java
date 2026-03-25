package com.gods.saas.security;

import com.gods.saas.service.impl.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        try {
            // ============================================================
            // 1. RUTAS PÚBLICAS
            // ============================================================
            if (path.startsWith("/api/auth/")
                    || path.startsWith("/swagger-ui/")
                    || path.startsWith("/v3/api-docs/")
                    || path.startsWith("/api/tenants")) {
                filterChain.doFilter(request, response);
                return;
            }

            // ============================================================
            // 2. EXTRAER TOKEN
            // ============================================================
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            // ============================================================
            // 3. LEER CLAIMS DEL TOKEN
            // ============================================================
            Claims claims = jwtService.extractAllClaims(token);
            String userType = claims.get("userType", String.class);

            UsernamePasswordAuthenticationToken authentication = null;
            Map<String, Object> details = new HashMap<>();

            // ============================================================
            // 4. TOKEN PARA CLIENTE
            // ============================================================
            if ("customer".equals(userType)) {
                Number customerIdNum = claims.get("customerId", Number.class);
                Number tenantIdNum = claims.get("tenantId", Number.class);

                if (customerIdNum == null) {
                    throw new JwtException("El token de cliente no contiene customerId");
                }
                if (tenantIdNum == null) {
                    throw new JwtException("El token de cliente no contiene tenantId");
                }

                Long customerId = customerIdNum.longValue();
                Long tenantId = tenantIdNum.longValue();

                authentication = new UsernamePasswordAuthenticationToken(
                        customerId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                );

                details.put("customerId", customerId);
                details.put("tenantId", tenantId);
                details.put("userType", userType);

                TenantContext.setTenantId(tenantId);

                request.setAttribute("customerId", customerId);
                request.setAttribute("tenantId", tenantId);
            }
            // ============================================================
            // 5. TOKEN PARA USUARIO INTERNO
            // ============================================================
            else if ("internal".equals(userType)) {
                Number userIdNum = claims.get("userId", Number.class);
                Number tenantIdNum = claims.get("tenantId", Number.class);
                Number branchIdNum = claims.get("branchId", Number.class);
                String role = claims.get("role", String.class);

                if (userIdNum == null) {
                    throw new JwtException("El token interno no contiene userId");
                }
                if (role == null || role.isBlank()) {
                    throw new JwtException("El token interno no contiene role");
                }

                Long userId = userIdNum.longValue();
                Long tenantId = tenantIdNum != null ? tenantIdNum.longValue() : null;
                Long branchId = branchIdNum != null ? branchIdNum.longValue() : null;

                SimpleGrantedAuthority authority =
                        new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());

                authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(authority)
                );

                details.put("userId", userId);
                details.put("tenantId", tenantId);
                details.put("branchId", branchId);
                details.put("role", role);
                details.put("userType", userType);

                TenantContext.setTenantId(tenantId);

                // 🔥 ESTO ES LO QUE FALTA
                request.setAttribute("userId", userId);
                request.setAttribute("tenantId", tenantId);
                request.setAttribute("branchId", branchId);
                request.setAttribute("role", role);
            }

            // ============================================================
            // 6. REGISTRAR AUTENTICACIÓN
            // ============================================================
            if (authentication != null) {
                authentication.setDetails(details);

                System.out.println("JWT DETAILS => " + details);
                System.out.println("JWT AUTH PRINCIPAL => " + authentication.getPrincipal());

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            TenantContext.clear();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                {
                  "code": "TOKEN_EXPIRED",
                  "message": "La sesión ha expirado"
                }
                """);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            TenantContext.clear();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                {
                  "code": "INVALID_TOKEN",
                  "message": "Token inválido o mal formado"
                }
                """);
        } finally {
            TenantContext.clear();
        }
    }
}