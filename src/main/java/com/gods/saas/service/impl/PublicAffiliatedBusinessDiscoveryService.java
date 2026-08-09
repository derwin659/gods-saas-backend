package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.PublicAffiliatedBranchResponse;
import com.gods.saas.domain.dto.response.PublicAffiliatedBranchDetailResponse;
import com.gods.saas.domain.model.BarberAvailability;
import com.gods.saas.domain.model.AffiliatedDiscoveryEvent;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.BarberAvailabilityRepository;
import com.gods.saas.domain.repository.AffiliatedDiscoveryEventRepository;
import com.gods.saas.domain.repository.PromotionRepository;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.domain.repository.VerifiedBusinessReviewRepository;
import com.gods.saas.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicAffiliatedBusinessDiscoveryService {

    private static final double DEFAULT_RADIUS_KM = 10.0;
    private static final double MAX_RADIUS_KM = 50.0;

    private final BranchRepository branchRepository;
    private final BarberAvailabilityRepository availabilityRepository;
    private final ServiceRepository serviceRepository;
    private final PromotionRepository promotionRepository;
    private final AffiliatedDiscoveryEventRepository eventRepository;
    private final VerifiedBusinessReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public List<PublicAffiliatedBranchResponse> search(Double latitude, Double longitude, String city, String businessType, String q, Double radiusKm, Integer limit) {
        boolean hasLocation = latitude != null && longitude != null;
        double effectiveRadius = normalizeRadius(radiusKm);
        int effectiveLimit = normalizeLimit(limit);
        String normalizedCity = normalizeCity(city);
        String normalizedBusinessType = normalizeBusinessType(businessType);
        String normalizedQuery = normalizeQuery(q);

        List<Branch> branches = normalizedCity == null
                ? branchRepository.findPublicDirectoryBranches()
                : branchRepository.findPublicDirectoryBranchesByCity(normalizedCity);

        return branches.stream()
                .filter(branch -> matchesBusinessType(branch, normalizedBusinessType))
                .filter(branch -> matchesQuery(branch, normalizedQuery))
                .map(branch -> toResponse(branch, hasLocation, latitude, longitude))
                .filter(item -> !hasLocation || item.distanceKm() == null || item.distanceKm() <= effectiveRadius)
                .sorted(comparator(hasLocation))
                .limit(effectiveLimit)
                .toList();
    }

    @Transactional
    public void recordEvent(Long branchId, String eventType) {
        String normalized = eventType == null ? "" : eventType.trim().toUpperCase();
        if (!java.util.Set.of("VIEW", "ROUTE", "BOOKING_INTENT", "BOOKING_CONFIRMED").contains(normalized)) {
            throw new BusinessException("Tipo de evento no valido");
        }
        Branch branch = branchRepository
                .findByIdAndActivoTrueAndPublicVisibleTrueAndDirectoryEnabledTrue(branchId)
                .filter(item -> item.getTenant() != null && Boolean.TRUE.equals(item.getTenant().getActive()))
                .orElseThrow(() -> new BusinessException("Negocio afiliado no disponible"));
        eventRepository.save(AffiliatedDiscoveryEvent.builder()
                .tenant(branch.getTenant()).branch(branch).eventType(normalized).build());
    }
    @Transactional(readOnly = true)
    public PublicAffiliatedBranchDetailResponse detail(Long branchId) {
        Branch branch = branchRepository
                .findByIdAndActivoTrueAndPublicVisibleTrueAndDirectoryEnabledTrue(branchId)
                .filter(item -> item.getTenant() != null && Boolean.TRUE.equals(item.getTenant().getActive()))
                .orElseThrow(() -> new BusinessException("Negocio afiliado no disponible"));
        Long tenantId = branch.getTenant().getId();
        int dayOfWeek = LocalDate.now().getDayOfWeek().getValue();
        List<BarberAvailability> availability = availabilityRepository
                .findByTenant_IdAndBranch_IdAndDayOfWeekAndIsWorkingTrueOrderByStartTimeAsc(
                        tenantId, branchId, dayOfWeek);
        LocalTime opensAt = availability.stream().map(BarberAvailability::getStartTime)
                .filter(java.util.Objects::nonNull).min(LocalTime::compareTo).orElse(null);
        LocalTime closesAt = availability.stream().map(BarberAvailability::getEndTime)
                .filter(java.util.Objects::nonNull).max(LocalTime::compareTo).orElse(null);
        LocalTime now = LocalTime.now();
        boolean openNow = opensAt != null && closesAt != null
                && !now.isBefore(opensAt) && now.isBefore(closesAt);
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
        String todayHours = opensAt == null || closesAt == null
                ? null : opensAt.format(timeFormat) + " - " + closesAt.format(timeFormat);
        String statusLabel = openNow
                ? "Abierto ahora"
                : opensAt == null
                    ? "Horario no disponible"
                    : now.isBefore(opensAt)
                        ? "Abre a las " + opensAt.format(timeFormat)
                        : "Cerrado hoy";

        var services = serviceRepository
                .findByTenant_IdAndActivoTrueAndDeletedAtIsNullOrderByNombreAsc(tenantId)
                .stream().limit(8)
                .map(item -> new PublicAffiliatedBranchDetailResponse.PublicServiceSummary(
                        item.getId(), item.getNombre(), item.getDescripcion(), item.getCategoria(),
                        item.getDuracionMinutos(), item.getPrecio(), item.getPrecioVariable(), item.getImageUrl()))
                .toList();
        var promotions = promotionRepository.findActiveClientPromotions(tenantId, 0).stream()
                .filter(item -> item.getBranch() == null || branchId.equals(item.getBranch().getId()))
                .filter(item -> !item.isSoloClientesConPuntos())
                .limit(5)
                .map(item -> new PublicAffiliatedBranchDetailResponse.PublicPromotionSummary(
                        item.getId(), item.getTitulo(), item.getSubtitulo(), item.getDescripcion(),
                        item.getBadge(), item.getImageUrl(), item.getPriceText()))
                .toList();

        var reviews = reviewRepository
                .findTop20ByBranch_IdAndCommentIsNotNullOrderByCreatedAtDesc(branchId)
                .stream()
                .filter(item -> item.getComment() != null && !item.getComment().isBlank())
                .filter(item -> !"HIDDEN".equalsIgnoreCase(item.getModerationStatus()))
                .map(item -> new PublicAffiliatedBranchDetailResponse.PublicReviewSummary(
                        item.getId(), item.getRating(), item.getComment().trim(),
                        publicCustomerName(item.getCustomer().getNombres(), item.getCustomer().getApellidos()),
                        item.getCreatedAt(), true, item.getOwnerReply(), item.getOwnerRepliedAt()))
                .toList();
        return new PublicAffiliatedBranchDetailResponse(
                toResponse(branch, false, null, null), openNow, statusLabel,
                todayHours, services, promotions, reviews);
    }
    private PublicAffiliatedBranchResponse toResponse(Branch branch, boolean hasLocation, Double latitude, Double longitude) {
        Tenant tenant = branch.getTenant();
        Double distance = null;
        if (hasLocation && branch.getLatitude() != null && branch.getLongitude() != null) {
            distance = haversineKm(latitude, longitude, branch.getLatitude(), branch.getLongitude());
        }

        String city = firstNonBlank(branch.getCiudad(), tenant == null ? null : tenant.getCiudad());
        boolean near = distance != null && distance <= 5.0;
        String availabilityLabel = distance == null
                ? "Disponible para reservar"
                : distance <= 5.0
                    ? "Cerca de ti"
                    : distance <= 10.0
                        ? "Disponible en tu zona"
                        : "Afiliado disponible";

        WalkInPublicStatus walkInStatus = resolveWalkInStatus(branch);
        return new PublicAffiliatedBranchResponse(
                tenant == null ? null : tenant.getId(),
                tenant == null ? null : tenant.getNombre(),
                tenant == null ? null : tenant.getCodigo(),
                tenant == null ? null : tenant.getLogoUrl(),
                tenant == null ? null : tenant.getBusinessType(),
                tenant == null ? null : tenant.getPais(),
                branch.getId(),
                branch.getNombre(),
                branch.getDireccion(),
                branch.getTelefono(),
                city,
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getImageUrl(),
                branch.getPublicDescription(),
                distance,
                distance == null ? null : String.format("%.1f km", distance),
                availabilityLabel,
                Boolean.TRUE.equals(branch.getWalkInEnabled()),
                Boolean.TRUE.equals(branch.getWalkInPaused()),
                branch.getWalkInEstimatedWaitMinutes(),
                branch.getWalkInMessage(),
                walkInStatus.label(),
                walkInStatus.availableNow(),
                near,
                reviewRepository.findAverageRatingByBranchId(branch.getId()),
                reviewRepository.countByBranch_IdAndModerationStatusNot(branch.getId(), "HIDDEN")
        );
    }

    private WalkInPublicStatus resolveWalkInStatus(Branch branch) {
        if (!Boolean.TRUE.equals(branch.getWalkInEnabled())) {
            return new WalkInPublicStatus("Solo con reserva", false);
        }
        if (Boolean.TRUE.equals(branch.getWalkInPaused())) {
            return new WalkInPublicStatus("Llegadas sin reserva pausadas", false);
        }
        int dayOfWeek = LocalDate.now().getDayOfWeek().getValue();
        var schedules = availabilityRepository
                .findByTenant_IdAndBranch_IdAndDayOfWeekAndIsWorkingTrueOrderByStartTimeAsc(
                        branch.getTenant().getId(), branch.getId(), dayOfWeek);
        LocalTime opensAt = schedules.stream().map(BarberAvailability::getStartTime)
                .filter(java.util.Objects::nonNull).min(LocalTime::compareTo).orElse(null);
        LocalTime closesAt = schedules.stream().map(BarberAvailability::getEndTime)
                .filter(java.util.Objects::nonNull).max(LocalTime::compareTo).orElse(null);
        if (opensAt == null || closesAt == null) return new WalkInPublicStatus("Cerrado hoy", false);
        LocalTime now = LocalTime.now();
        if (now.isBefore(opensAt)) {
            return new WalkInPublicStatus("Sin reserva desde " + opensAt.format(DateTimeFormatter.ofPattern("HH:mm")), false);
        }
        if (!now.isBefore(closesAt)) return new WalkInPublicStatus("Cerrado hoy", false);
        Integer wait = branch.getWalkInEstimatedWaitMinutes();
        if (wait == null || wait <= 0) return new WalkInPublicStatus("Disponible sin reserva", true);
        return new WalkInPublicStatus("Espera aprox. " + wait + " min", true);
    }

    private record WalkInPublicStatus(String label, boolean availableNow) {}
    private Comparator<PublicAffiliatedBranchResponse> comparator(boolean hasLocation) {
        Comparator<PublicAffiliatedBranchResponse> byName = Comparator
                .comparing((PublicAffiliatedBranchResponse item) -> safe(item.tenantName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> safe(item.branchName()), String.CASE_INSENSITIVE_ORDER);

        if (!hasLocation) return byName;

        return Comparator
                .comparing((PublicAffiliatedBranchResponse item) -> item.distanceKm() == null ? Double.MAX_VALUE : item.distanceKm())
                .thenComparing(byName);
    }

    private double normalizeRadius(Double radiusKm) {
        if (radiusKm == null || radiusKm <= 0) return DEFAULT_RADIUS_KM;
        return Math.min(radiusKm, MAX_RADIUS_KM);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) return 30;
        return Math.min(limit, 100);
    }

    private String normalizeCity(String city) {
        if (city == null || city.trim().isEmpty()) return null;
        return city.trim();
    }

    private String normalizeQuery(String q) {
        if (q == null || q.trim().isEmpty()) return null;
        return q.trim().toLowerCase();
    }

    private boolean matchesQuery(Branch branch, String q) {
        if (q == null) return true;
        Tenant tenant = branch.getTenant();
        if (contains(tenant == null ? null : tenant.getNombre(), q)
                || contains(branch.getNombre(), q)
                || contains(branch.getDireccion(), q)
                || contains(branch.getCiudad(), q)
                || contains(branch.getPublicDescription(), q)) {
            return true;
        }
        if (tenant == null) return false;
        return serviceRepository
                .findByTenant_IdAndActivoTrueAndDeletedAtIsNullOrderByNombreAsc(tenant.getId())
                .stream()
                .anyMatch(service -> contains(service.getNombre(), q)
                        || contains(service.getDescripcion(), q)
                        || contains(service.getCategoria(), q));
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }
    private String normalizeBusinessType(String businessType) {
        if (businessType == null || businessType.trim().isEmpty()) return null;
        String normalized = businessType.trim().toUpperCase();
        if (normalized.equals("ALL") || normalized.equals("TODOS")) return null;
        if (normalized.equals("SALON")) return "BEAUTY_SALON";
        if (normalized.equals("HAIR")) return "HAIR_SALON";
        if (normalized.equals("NAIL")) return "NAILS";
        return normalized;
    }

    private boolean matchesBusinessType(Branch branch, String businessType) {
        if (businessType == null) return true;
        Tenant tenant = branch.getTenant();
        String tenantBusinessType = tenant == null || tenant.getBusinessType() == null
                ? "BARBERSHOP"
                : tenant.getBusinessType().trim().toUpperCase();
        if (businessType.equals("BEAUTY_SALON")) {
            return tenantBusinessType.equals("BEAUTY_SALON") || tenantBusinessType.equals("HAIR_SALON");
        }
        return tenantBusinessType.equals(businessType);
    }

    private String publicCustomerName(String firstNames, String lastNames) {
        String firstName = firstNames == null || firstNames.isBlank()
                ? "Cliente"
                : firstNames.trim().split("\\s+")[0];
        if (lastNames == null || lastNames.isBlank()) return firstName;
        String lastName = lastNames.trim().split("\\s+")[0];
        return lastName.isEmpty() ? firstName : firstName + " " + lastName.substring(0, 1).toUpperCase() + ".";
    }
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        if (second != null && !second.trim().isEmpty()) return second.trim();
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadiusKm * c * 10.0) / 10.0;
    }
}