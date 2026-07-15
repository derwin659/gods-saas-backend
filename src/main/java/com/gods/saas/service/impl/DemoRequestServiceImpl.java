package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateDemoRequest;
import com.gods.saas.domain.dto.request.ReviewDemoRequest;
import com.gods.saas.domain.dto.response.DemoRequestResponse;
import com.gods.saas.domain.enums.BusinessType;
import com.gods.saas.domain.enums.DemoRequestStatus;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.DemoRequestService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class DemoRequestServiceImpl implements DemoRequestService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";
    private static final String DEFAULT_PASSWORD = "123456";
    private static final int TRIAL_DAYS = 7;
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DemoRequestRepository demoRequestRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionPlanPricingService pricingService;
    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;

    @Override
    public DemoRequestResponse createPublicRequest(CreateDemoRequest request) {
        validateCreateRequest(request);

        String email = normalizeEmail(request.getOwnerEmail());
        String phone = trim(request.getOwnerPhone());

        if (demoRequestRepository.existsByOwnerEmailIgnoreCaseAndStatus(email, DemoRequestStatus.PENDING_REVIEW)) {
            throw new IllegalArgumentException("Ya existe una solicitud pendiente con este correo.");
        }

        if (demoRequestRepository.existsByOwnerPhoneAndStatus(phone, DemoRequestStatus.PENDING_REVIEW)) {
            throw new IllegalArgumentException("Ya existe una solicitud pendiente con este WhatsApp.");
        }

        DemoRequest demoRequest = DemoRequest.builder()
                .businessName(trim(request.getBusinessName()))
                .businessType(request.getBusinessType() != null ? request.getBusinessType() : BusinessType.BARBERSHOP)
                .ownerName(trim(request.getOwnerName()))
                .ownerEmail(email)
                .ownerPhone(phone)
                .country(trimToNull(request.getCountry()))
                .city(trimToNull(request.getCity()))
                .branchesCount(request.getBranchesCount())
                .professionalsCount(request.getProfessionalsCount())
                .socialLink(trimToNull(request.getSocialLink()))
                .googleMapsLink(trimToNull(request.getGoogleMapsLink()))
                .message(trimToNull(request.getMessage()))
                .status(DemoRequestStatus.PENDING_REVIEW)
                .createdAt(nowLima())
                .build();

        return mapToResponse(demoRequestRepository.save(demoRequest));
    }

    @Override
    public DemoRequestResponse activatePublicTrial(CreateDemoRequest request) {
        validateCreateRequest(request);

        GoogleOAuthService.GoogleSignupProfile googleProfile = resolveGoogleSignupProfile(request);
        String email = googleProfile != null
                ? normalizeEmail(googleProfile.email())
                : normalizeEmail(request.getOwnerEmail());
        String ownerName = googleProfile != null && !isBlank(googleProfile.name())
                ? trim(googleProfile.name())
                : trim(request.getOwnerName());
        String phone = trim(request.getOwnerPhone());

        if (appUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Ya existe una cuenta con este correo. Inicia sesion o vincula Gmail desde seguridad.");
        }

        if (googleProfile != null && appUserRepository.findByGoogleSubject(googleProfile.subject()).isPresent()) {
            throw new IllegalArgumentException("Este Gmail ya esta vinculado a otra cuenta.");
        }

        if (demoRequestRepository.existsByOwnerEmailIgnoreCaseAndStatus(email, DemoRequestStatus.PENDING_REVIEW)) {
            throw new IllegalArgumentException("Ya existe una solicitud pendiente con este correo.");
        }

        if (demoRequestRepository.existsByOwnerPhoneAndStatus(phone, DemoRequestStatus.PENDING_REVIEW)) {
            throw new IllegalArgumentException("Ya existe una solicitud pendiente con este WhatsApp.");
        }

        LocalDateTime now = nowLima();

        DemoRequest demoRequest = DemoRequest.builder()
                .businessName(trim(request.getBusinessName()))
                .businessType(request.getBusinessType() != null ? request.getBusinessType() : BusinessType.BARBERSHOP)
                .ownerName(ownerName)
                .ownerEmail(email)
                .ownerPhone(phone)
                .country(trimToNull(request.getCountry()))
                .city(trimToNull(request.getCity()))
                .branchesCount(request.getBranchesCount())
                .professionalsCount(request.getProfessionalsCount())
                .socialLink(trimToNull(request.getSocialLink()))
                .googleMapsLink(trimToNull(request.getGoogleMapsLink()))
                .message(trimToNull(request.getMessage()))
                .status(DemoRequestStatus.PENDING_REVIEW)
                .createdAt(now)
                .build();

        demoRequest = demoRequestRepository.save(demoRequest);

        Tenant tenant = createTenantFromDemoRequest(demoRequest, now);
        Branch branch = createMainBranch(tenant, demoRequest, now);
        String ownerPassword = resolveOwnerPassword(request, googleProfile);
        AppUser owner = createOwnerUser(tenant, branch, demoRequest, now, ownerPassword);
        if (googleProfile != null) {
            linkOwnerWithGoogle(owner, googleProfile, now);
        }
        createOwnerRole(owner, tenant, branch);
        createTrialSubscription(tenant, now);
        createTenantSettings(tenant, now);

        demoRequest.setStatus(DemoRequestStatus.CONVERTED_TO_TENANT);
        demoRequest.setReviewNotes("Autoactivada desde registro publico.");
        demoRequest.setReviewedAt(now);
        demoRequest.setCreatedTenantId(tenant.getId());

        return mapToResponseWithAccess(
                demoRequestRepository.save(demoRequest),
                null,
                googleProfile,
                buildOwnerSession(owner, tenant, branch)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DemoRequestResponse> findAll() {
        return demoRequestRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DemoRequestResponse> findPending() {
        return demoRequestRepository.findByStatusOrderByCreatedAtDesc(DemoRequestStatus.PENDING_REVIEW)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DemoRequestResponse findById(Long id) {
        return demoRequestRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada: " + id));
    }

    @Override
    public DemoRequestResponse approve(Long id, ReviewDemoRequest request) {
        DemoRequest demoRequest = getRequestOrThrow(id);

        if (demoRequest.getStatus() == DemoRequestStatus.CONVERTED_TO_TENANT) {
            throw new IllegalStateException("Esta solicitud ya fue convertida a tenant.");
        }

        if (appUserRepository.existsByEmail(demoRequest.getOwnerEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con este correo: " + demoRequest.getOwnerEmail());
        }

        LocalDateTime now = nowLima();

        Tenant tenant = createTenantFromDemoRequest(demoRequest, now);
        Branch branch = createMainBranch(tenant, demoRequest, now);
        AppUser owner = createOwnerUser(tenant, branch, demoRequest, now, DEFAULT_PASSWORD);
        createOwnerRole(owner, tenant, branch);
        createTrialSubscription(tenant, now);
        createTenantSettings(tenant, now);

        demoRequest.setStatus(DemoRequestStatus.CONVERTED_TO_TENANT);
        demoRequest.setReviewNotes(trimToNull(request != null ? request.getNotes() : null));
        demoRequest.setReviewedBy(request != null ? request.getReviewedBy() : null);
        demoRequest.setReviewedAt(now);
        demoRequest.setCreatedTenantId(tenant.getId());

        return mapToResponse(demoRequestRepository.save(demoRequest));
    }

    @Override
    public DemoRequestResponse reject(Long id, ReviewDemoRequest request) {
        DemoRequest demoRequest = getRequestOrThrow(id);
        LocalDateTime now = nowLima();

        demoRequest.setStatus(DemoRequestStatus.REJECTED);
        demoRequest.setReviewNotes(trimToNull(request != null ? request.getNotes() : null));
        demoRequest.setReviewedBy(request != null ? request.getReviewedBy() : null);
        demoRequest.setReviewedAt(now);

        return mapToResponse(demoRequestRepository.save(demoRequest));
    }

    @Override
    public DemoRequestResponse markSuspicious(Long id, ReviewDemoRequest request) {
        DemoRequest demoRequest = getRequestOrThrow(id);
        LocalDateTime now = nowLima();

        demoRequest.setStatus(DemoRequestStatus.SUSPICIOUS);
        demoRequest.setReviewNotes(trimToNull(request != null ? request.getNotes() : null));
        demoRequest.setReviewedBy(request != null ? request.getReviewedBy() : null);
        demoRequest.setReviewedAt(now);

        return mapToResponse(demoRequestRepository.save(demoRequest));
    }

    private Tenant createTenantFromDemoRequest(DemoRequest demoRequest, LocalDateTime now) {
        Tenant tenant = new Tenant();
        tenant.setNombre(demoRequest.getBusinessName());
        tenant.setOwnerName(demoRequest.getOwnerName());
        tenant.setCodigo(generateTenantCode(demoRequest.getBusinessName()));
        tenant.setPais(demoRequest.getCountry());
        tenant.setCiudad(demoRequest.getCity());
        tenant.setBusinessType(demoRequest.getBusinessType().name());
        tenant.setPlan("STARTER");
        tenant.setEstadoSuscripcion("TRIAL");
        tenant.setActive(true);
        tenant.setFechaCreacion(now);
        tenant.setFechaActualizacion(now);
        return tenantRepository.save(tenant);
    }

    private Branch createMainBranch(Tenant tenant, DemoRequest demoRequest, LocalDateTime now) {
        Branch branch = new Branch();
        branch.setTenant(tenant);
        branch.setNombre("Principal");
        branch.setDireccion(null);
        branch.setTelefono(demoRequest.getOwnerPhone());
        branch.setActivo(true);
        branch.setFechaCreacion(now);
        return branchRepository.save(branch);
    }

    private AppUser createOwnerUser(
            Tenant tenant,
            Branch branch,
            DemoRequest demoRequest,
            LocalDateTime now,
            String temporaryPassword
    ) {
        AppUser owner = new AppUser();
        owner.setTenant(tenant);
        owner.setBranch(branch);
        owner.setNombre(demoRequest.getOwnerName());
        owner.setEmail(demoRequest.getOwnerEmail());
        owner.setPhone(demoRequest.getOwnerPhone());
        owner.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        owner.setRol("OWNER");
        owner.setActivo(true);
        owner.setFechaCreacion(now);
        owner.setFechaActualizacion(now);
        return appUserRepository.save(owner);
    }

    private void linkOwnerWithGoogle(
            AppUser owner,
            GoogleOAuthService.GoogleSignupProfile profile,
            LocalDateTime now
    ) {
        owner.setGoogleSubject(profile.subject());
        owner.setGoogleEmail(profile.email());
        owner.setGoogleName(profile.name());
        owner.setGooglePictureUrl(profile.pictureUrl());
        owner.setGoogleLinkedAt(now);
        appUserRepository.save(owner);
    }

    private com.gods.saas.domain.dto.LoginFinalResponse buildOwnerSession(
            AppUser owner,
            Tenant tenant,
            Branch branch
    ) {
        String token = jwtService.generateToken(owner, tenant.getId(), RoleType.OWNER.name(), branch.getId());
        return com.gods.saas.domain.dto.LoginFinalResponse.builder()
                .token(token)
                .userId(owner.getId())
                .nombre(owner.getNombre())
                .email(owner.getEmail())
                .tenantId(tenant.getId())
                .tenantName(tenant.getNombre())
                .businessType(tenant.getBusinessType())
                .branchId(branch.getId())
                .branchName(branch.getNombre())
                .role(RoleType.OWNER.name())
                .build();
    }

    private void createOwnerRole(AppUser owner, Tenant tenant, Branch branch) {
        UserTenantRole role = new UserTenantRole();
        role.setUser(owner);
        role.setTenant(tenant);
        role.setRole(RoleType.OWNER);
        role.setBranch(branch);
        userTenantRoleRepository.save(role);
    }

    private void createTrialSubscription(Tenant tenant, LocalDateTime now) {
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenant.getId());
        subscription.setPlan("STARTER");
        subscription.setPrecioMensual(pricingService.resolveMonthlyPrice("STARTER", tenant.getPais(), resolveCurrencyForCountry(tenant.getPais())).doubleValue());
        subscription.setEstado("TRIAL");
        subscription.setFechaInicio(now);
        subscription.setFechaRenovacion(now.plusDays(TRIAL_DAYS));
        subscription.setFechaFin(now.plusDays(TRIAL_DAYS));
        subscription.setTrial(true);
        subscription.setDiasGracia(0);
        subscription.setMaxBranches(1);
        subscription.setMaxBarbers(5);
        subscription.setMaxAdmins(1);
        subscription.setAiEnabled(false);
        subscription.setLoyaltyEnabled(true);
        subscription.setPromotionsEnabled(true);
        subscription.setCustomRewardsEnabled(true);
        subscription.setBillingCycle("MONTHLY");
        subscription.setCurrency(resolveCurrencyForCountry(tenant.getPais()));
        subscription.setObservaciones("Trial inicial creado desde solicitud pÃºblica de demo");
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);

        subscriptionRepository.save(subscription);
    }

    private void createTenantSettings(Tenant tenant, LocalDateTime now) {
        TenantSettings settings = new TenantSettings();
        settings.setTenant(tenant);
        settings.setLanguage("es");
        settings.setTimezone(DEFAULT_TIMEZONE);
        settings.setCurrency(resolveCurrencyForCountry(tenant.getPais()));
        settings.setScheduleConfig(new HashMap<>());
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);

        tenantSettingsRepository.save(settings);
    }

    private DemoRequest getRequestOrThrow(Long id) {
        return demoRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada: " + id));
    }

    private void validateCreateRequest(CreateDemoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud es obligatoria.");
        }
        if (isBlank(request.getBusinessName())) {
            throw new IllegalArgumentException("El nombre del negocio es obligatorio.");
        }
        if (isBlank(request.getOwnerName())) {
            throw new IllegalArgumentException("El nombre del dueÃ±o es obligatorio.");
        }
        if (isBlank(request.getOwnerEmail())) {
            throw new IllegalArgumentException("El correo del dueÃ±o es obligatorio.");
        }
        if (isBlank(request.getOwnerPhone())) {
            throw new IllegalArgumentException("El WhatsApp del dueÃ±o es obligatorio.");
        }
    }

    private DemoRequestResponse mapToResponse(DemoRequest demoRequest) {
        return DemoRequestResponse.builder()
                .id(demoRequest.getId())
                .businessName(demoRequest.getBusinessName())
                .businessType(demoRequest.getBusinessType())
                .ownerName(demoRequest.getOwnerName())
                .ownerEmail(demoRequest.getOwnerEmail())
                .ownerPhone(demoRequest.getOwnerPhone())
                .country(demoRequest.getCountry())
                .city(demoRequest.getCity())
                .branchesCount(demoRequest.getBranchesCount())
                .professionalsCount(demoRequest.getProfessionalsCount())
                .socialLink(demoRequest.getSocialLink())
                .googleMapsLink(demoRequest.getGoogleMapsLink())
                .message(demoRequest.getMessage())
                .status(demoRequest.getStatus())
                .reviewNotes(demoRequest.getReviewNotes())
                .reviewedBy(demoRequest.getReviewedBy())
                .createdTenantId(demoRequest.getCreatedTenantId())
                .createdAt(demoRequest.getCreatedAt())
                .reviewedAt(demoRequest.getReviewedAt())
                .build();
    }

    private DemoRequestResponse mapToResponseWithAccess(
            DemoRequest demoRequest,
            String temporaryPassword,
            GoogleOAuthService.GoogleSignupProfile googleProfile,
            com.gods.saas.domain.dto.LoginFinalResponse session
    ) {
        return DemoRequestResponse.builder()
                .id(demoRequest.getId())
                .businessName(demoRequest.getBusinessName())
                .businessType(demoRequest.getBusinessType())
                .ownerName(demoRequest.getOwnerName())
                .ownerEmail(demoRequest.getOwnerEmail())
                .ownerPhone(demoRequest.getOwnerPhone())
                .country(demoRequest.getCountry())
                .city(demoRequest.getCity())
                .branchesCount(demoRequest.getBranchesCount())
                .professionalsCount(demoRequest.getProfessionalsCount())
                .socialLink(demoRequest.getSocialLink())
                .googleMapsLink(demoRequest.getGoogleMapsLink())
                .message(demoRequest.getMessage())
                .status(demoRequest.getStatus())
                .reviewNotes(demoRequest.getReviewNotes())
                .reviewedBy(demoRequest.getReviewedBy())
                .createdTenantId(demoRequest.getCreatedTenantId())
                .accessEmail(demoRequest.getOwnerEmail())
                .temporaryPassword(temporaryPassword)
                .trialDays(TRIAL_DAYS)
                .googleLinked(googleProfile != null)
                .googlePictureUrl(googleProfile != null ? googleProfile.pictureUrl() : null)
                .session(session)
                .createdAt(demoRequest.getCreatedAt())
                .reviewedAt(demoRequest.getReviewedAt())
                .build();
    }

    private GoogleOAuthService.GoogleSignupProfile resolveGoogleSignupProfile(CreateDemoRequest request) {
        String token = request.getGoogleSignupToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        return googleOAuthService.verifySignupToken(token.trim());
    }

    private String resolveOwnerPassword(
            CreateDemoRequest request,
            GoogleOAuthService.GoogleSignupProfile googleProfile
    ) {
        String password = trim(request.getPassword());

        if (password.length() >= 6) {
            return password;
        }

        if (googleProfile != null) {
            return generateTemporaryPassword();
        }

        throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
    }

    private String generateTemporaryPassword() {
        StringBuilder value = new StringBuilder("SG-");
        for (int i = 0; i < 10; i++) {
            value.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return value.toString();
    }

    private String generateTenantCode(String businessName) {
        String base = businessName
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", "")
                .trim()
                .replaceAll("\\s+", "-");

        if (base.isBlank()) {
            base = "NEGOCIO";
        }

        if (base.length() > 15) {
            base = base.substring(0, 15);
        }

        String code = base;
        int i = 2;

        while (tenantRepository.existsByCodigo(code)) {
            code = base + "-" + i;
            i++;
        }

        return code;
    }

    private LocalDateTime nowLima() {
        return LocalDateTime.now(ZoneId.of(DEFAULT_TIMEZONE));
    }

    private String normalizeEmail(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveCurrencyForCountry(String country) {
        String normalizedCountry = normalizeCountry(country);
        return switch (normalizedCountry) {
            case "PERU", "PE" -> "PEN";
            case "ESTADOSUNIDOS", "UNITEDSTATES", "USA", "US" -> "USD";
            case "COLOMBIA", "CO" -> "COP";
            case "MEXICO", "MX" -> "MXN";
            case "CHILE", "CL" -> "CLP";
            case "ARGENTINA", "AR" -> "ARS";
            case "BOLIVIA", "BO" -> "BOB";
            case "BRASIL", "BRAZIL", "BR" -> "BRL";
            case "VENEZUELA", "VE" -> "USD";
            case "URUGUAY", "UY" -> "UYU";
            case "PARAGUAY", "PY" -> "PYG";
            case "COSTARICA", "CR" -> "CRC";
            case "REPUBLICADOMINICANA", "DOMINICANREPUBLIC", "DO" -> "DOP";
            case "GUATEMALA", "GT" -> "GTQ";
            case "ESPANA", "SPAIN", "EUROPA", "EUROPE", "EU" -> "EUR";
            default -> "PEN";
        };
    }

    private String normalizeCountry(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "")
                .toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

