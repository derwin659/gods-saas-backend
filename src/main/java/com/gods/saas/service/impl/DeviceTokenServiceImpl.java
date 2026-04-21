package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.RegisterDeviceTokenRequest;
import com.gods.saas.domain.dto.response.DeviceTokenResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.DeviceToken;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.DeviceTokenRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.DeviceTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final AppUserRepository appUserRepository;

    @Override
    public DeviceTokenResponse registerCustomerToken(Long tenantId, Long customerId, RegisterDeviceTokenRequest request) {
        validateRequest(request);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        Customer customer = customerRepository.findByTenant_IdAndId(tenantId, customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        deactivateSameTokenElsewhere(request.getToken(), tenantId, customerId, null);

        DeviceToken entity = deviceTokenRepository
                .findByTenant_IdAndCustomer_IdAndToken(tenantId, customerId, request.getToken())
                .orElseGet(() -> DeviceToken.builder()
                        .tenant(tenant)
                        .customer(customer)
                        .token(request.getToken().trim())
                        .build());

        entity.setCustomer(customer);
        entity.setUser(null);
        entity.setPlatform(normalizePlatform(request.getPlatform()));
        entity.setActive(true);

        DeviceToken saved = deviceTokenRepository.save(entity);

        return DeviceTokenResponse.builder()
                .id(saved.getId())
                .token(saved.getToken())
                .platform(saved.getPlatform())
                .active(saved.getActive())
                .message("Token del cliente registrado correctamente")
                .build();
    }

    @Override
    public DeviceTokenResponse registerUserToken(Long tenantId, Long userId, RegisterDeviceTokenRequest request) {
        validateRequest(request);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        AppUser user = appUserRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        deactivateSameTokenElsewhere(request.getToken(), tenantId, null, userId);

        DeviceToken entity = deviceTokenRepository
                .findByTenant_IdAndUser_IdAndToken(tenantId, userId, request.getToken())
                .orElseGet(() -> DeviceToken.builder()
                        .tenant(tenant)
                        .user(user)
                        .token(request.getToken().trim())
                        .build());

        entity.setUser(user);
        entity.setCustomer(null);
        entity.setPlatform(normalizePlatform(request.getPlatform()));
        entity.setActive(true);

        DeviceToken saved = deviceTokenRepository.save(entity);

        return DeviceTokenResponse.builder()
                .id(saved.getId())
                .token(saved.getToken())
                .platform(saved.getPlatform())
                .active(saved.getActive())
                .message("Token del usuario registrado correctamente")
                .build();
    }

    @Override
    public void deactivateCustomerToken(Long tenantId, Long customerId, String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Token obligatorio");
        }

        DeviceToken entity = deviceTokenRepository
                .findByTenant_IdAndCustomer_IdAndToken(tenantId, customerId, token.trim())
                .orElseThrow(() -> new RuntimeException("Token no encontrado"));

        entity.setActive(false);
        deviceTokenRepository.save(entity);
    }

    @Override
    public void deactivateUserToken(Long tenantId, Long userId, String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Token obligatorio");
        }

        DeviceToken entity = deviceTokenRepository
                .findByTenant_IdAndUser_IdAndToken(tenantId, userId, token.trim())
                .orElseThrow(() -> new RuntimeException("Token no encontrado"));

        entity.setActive(false);
        deviceTokenRepository.save(entity);
    }

    private void deactivateSameTokenElsewhere(String rawToken, Long tenantId, Long customerId, Long userId) {
        String token = rawToken.trim();

        List<DeviceToken> existing = deviceTokenRepository.findByToken(token);
        for (DeviceToken item : existing) {
            boolean sameTenant = item.getTenant() != null && item.getTenant().getId().equals(tenantId);
            boolean sameCustomer = customerId != null
                    && item.getCustomer() != null
                    && item.getCustomer().getId().equals(customerId);
            boolean sameUser = userId != null
                    && item.getUser() != null
                    && item.getUser().getId().equals(userId);

            if (!(sameTenant && (sameCustomer || sameUser))) {
                item.setActive(false);
                deviceTokenRepository.save(item);
            }
        }
    }

    private void validateRequest(RegisterDeviceTokenRequest request) {
        if (request == null) {
            throw new RuntimeException("Request obligatorio");
        }

        if (request.getToken() == null || request.getToken().trim().isEmpty()) {
            throw new RuntimeException("Token obligatorio");
        }
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        return platform.trim().toUpperCase(Locale.ROOT);
    }
}