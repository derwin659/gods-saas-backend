package com.gods.saas.security;

import com.gods.saas.service.impl.UserDetailsServiceImpl;
import com.gods.saas.utils.TvAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.http.HttpMethod;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final TvAuthFilter tvAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(authenticationProvider()));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // Necesario para CORS preflight desde React/Vercel/local
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                "/api/auth/login-basic",
                                "/api/auth/**",
                                "/api/auth/cliente/register",
                                "/api/auth/cliente/otp/request",
                                "/api/auth/cliente/otp/verify",
                                "/api/clients/clientes/phone/**",
                                "/api/clients/clientes/*/telefono/**",
                                "/api/bootstrap/**",
                                "/api/tenants/**",
                                "/api/internal/login",
                                "/api/internal/register",
                                "/api/ia/tv/*",
                                "/api/ia/tv/*/tomarsesion",
                                "/api/ia/tv/*/estado",
                                "/tv/**",
                                "/ws/**",
                                "/topic/**",
                                "/app/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/index.html",
                                "/*",
                                "/**/*.css",
                                "/**/*.js",
                                "/**/*.png",
                                "/**/*.jpg",
                                "/**/*.svg"
                        ).permitAll()

                        .requestMatchers("/api/admin/rewards/redemptions/**")
                        .hasAnyRole("OWNER", "ADMIN", "BARBER")

                        .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")

                        .requestMatchers("/api/internal/users/**").hasRole("OWNER")
                        .requestMatchers("/api/internal/users/login/**").hasRole("OWNER")
                        .requestMatchers("/api/internal/me")
                        .hasAnyRole("OWNER", "ADMIN", "BARBER", "CASHIER", "SUPER_ADMIN")

                        .requestMatchers("/api/owner/device-tokens/**")
                        .hasAnyRole("OWNER", "ADMIN", "BARBER", "CASHIER")

                        .requestMatchers("/api/owner/home/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/owner/agenda/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/owner/reports/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/owner/customers/**").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers("/api/owner/notifications/**")
                        .hasAnyRole("OWNER", "ADMIN", "BARBER", "CASHIER")

                        .requestMatchers("/api/clients/device-tokens/**")
                        .authenticated()

                        .requestMatchers("/api/owner/admin-permissions/me")
                        .hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers("/api/owner/admin-permissions/**")
                        .hasRole("OWNER")

                        .requestMatchers("/api/owner/cash-registers/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/owner/cash-sales/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/owner/marketing-campaigns/**").hasRole("OWNER")
                        .requestMatchers("/api/owner/products/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/owner/sale-catalog/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/owner/services/**").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers("/api/customers/quick")
                        .hasAnyRole("OWNER", "ADMIN", "BARBER", "CASHIER")

                        .requestMatchers("/api/cash-register/current").hasAnyRole("OWNER", "BARBER")
                        .requestMatchers("/api/barber/**").hasAnyRole("OWNER", "ADMIN", "BARBER")
                        .requestMatchers("/api/sales/**").hasAnyRole("OWNER", "ADMIN", "BARBER")
                        .requestMatchers("/api/sales").hasAnyRole("OWNER", "ADMIN", "BARBER")

                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(tvAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}