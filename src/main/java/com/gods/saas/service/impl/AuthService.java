package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.SendOtpRequest;
import com.gods.saas.domain.dto.VerifyOtpRequest;
import com.gods.saas.domain.dto.VerifyPhoneResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.OtpCode;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.OtpCodeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomerRepository customerRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final JwtService jwtService;

    /**
     * 1) Enviar OTP (generar y guardar)
     */

    @Transactional
    public void sendOtp(SendOtpRequest req) {

        // 1. Generar código OTP
        String code = String.format("%06d", new Random().nextInt(999999));

        // 2. Crear registro OTP (SIEMPRE)
        OtpCode otp = OtpCode.builder()
                .phone(req.getPhone())
                .tenantId(req.getTenantId())
                .code(code)
                .used(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();

        otpCodeRepository.save(otp);

        // 3. A futuro puedes enviar SMS o WhatsApp
        System.out.println("OTP generado: " + code);
    }


    /**
     * 2) Verificar OTP y devolver token + datos de usuario
     */

    @Transactional
    public VerifyPhoneResponse verifyOtp(VerifyOtpRequest req) {

        // 1. Buscar OTP válido (último y no usado)
        OtpCode otp = otpCodeRepository
                .findTopByPhoneAndTenantIdAndUsedIsFalseOrderByCreatedAtDesc(
                        req.getPhone(), req.getTenantId()
                )
                .orElseThrow(() -> new RuntimeException("No hay OTP generado para este teléfono"));

        // 2. Validar expiración
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El código OTP ha expirado");
        }

        // 3. Validar código
        if (!otp.getCode().equals(req.getCode())) {
            throw new RuntimeException("Código OTP incorrecto");
        }

        // 4. Marcar OTP como usado
        otp.setUsed(true);
        otpCodeRepository.save(otp);

        // 5. Buscar usuario por phonePendiente (NO por phone normal)
        Customer user = customerRepository
                .findByPhonePendienteAndTenantId(req.getPhone(), req.getTenantId())
                .orElse(null);

        if (user != null && user.isPhonePendienteVerificacion()) {

            // COMPLETAR CAMBIO DE NÚMERO
            user.setTelefono(user.getPhonePendiente());   // asignar nuevo número real
            user.setPhonePendiente(null);              // limpiar variable temporal
            user.setPhonePendienteVerificacion(false); // ya no está verificando
            user.setPhoneVerified(true);               // marcar teléfono como verificado
            user.setFechaActualizacion(LocalDateTime.now());

            customerRepository.save(user);

            return new VerifyPhoneResponse(true);
        }

        // 🔥 Si NO es cambio de teléfono → es verificación normal (registro o login)
        user = customerRepository.findByTelefonoAndTenantId(req.getPhone(), req.getTenantId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setPhoneVerified(true);
        user.setFechaActualizacion(LocalDateTime.now());
        customerRepository.save(user);

        return new VerifyPhoneResponse(true);
    }





    @Transactional
    public void iniciarCambioTelefono(Long userId, String nuevoTelefono) {

        Customer user = customerRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Verificar que no exista otro usuario con ese número
        customerRepository.findByTelefonoAndTenantId(nuevoTelefono, user.getTenant().getId())
                .ifPresent(u -> {
                    throw new RuntimeException("Ese número ya está en uso");
                });

        // GUARDAMOS EL NUEVO TELÉFONO SOLO COMO PENDIENTE
        user.setPhonePendiente(nuevoTelefono);
        user.setPhonePendienteVerificacion(true);
        user.setPhoneVerified(false);

        customerRepository.save(user);

        // Generar OTP para el nuevo número
        String code = String.format("%06d", new Random().nextInt(999999));
        OtpCode otp = OtpCode.builder()
                .phone(nuevoTelefono)
                .tenantId(user.getTenant().getId())
                .code(code)
                .used(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();

        otpCodeRepository.save(otp);

        System.out.println("OTP cambio teléfono: " + code);
    }



}

