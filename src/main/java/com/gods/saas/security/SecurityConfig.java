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

                        // PRIMERO las rutas más específicas
                        .requestMatchers("/api/admin/rewards/redemptions/**")
                        .hasAnyRole("OWNER", "ADMIN", "BARBER")
                        .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/internal/users/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/internal/users/login/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/internal/me").hasAnyRole("OWNER", "ADMIN", "BARBER")
                        .requestMatchers("/api/owner/**").hasRole("OWNER")
                        .requestMatchers("/api/cash-register/current").hasAnyRole("OWNER", "BARBER")
                        .requestMatchers("/api/barber/**").hasAnyRole("OWNER", "ADMIN", "BARBER")
                        .requestMatchers("/api/sales/**").hasAnyRole("OWNER", "ADMIN", "BARBER")
                        .requestMatchers("/api/sales").hasAnyRole("OWNER", "ADMIN", "BARBER")

                        // DESPUÉS la ruta general


                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(tvAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}