package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.PublicAffiliatedBranchResponse;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicAffiliatedBusinessDiscoveryService {

    private static final double DEFAULT_RADIUS_KM = 10.0;
    private static final double MAX_RADIUS_KM = 50.0;

    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<PublicAffiliatedBranchResponse> search(Double latitude, Double longitude, String city, Double radiusKm, Integer limit) {
        boolean hasLocation = latitude != null && longitude != null;
        double effectiveRadius = normalizeRadius(radiusKm);
        int effectiveLimit = normalizeLimit(limit);
        String normalizedCity = normalizeCity(city);

        return branchRepository.findPublicDirectoryBranches(normalizedCity)
                .stream()
                .map(branch -> toResponse(branch, hasLocation, latitude, longitude))
                .filter(item -> !hasLocation || item.distanceKm() == null || item.distanceKm() <= effectiveRadius)
                .sorted(comparator(hasLocation))
                .limit(effectiveLimit)
                .toList();
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

        return new PublicAffiliatedBranchResponse(
                tenant == null ? null : tenant.getId(),
                tenant == null ? null : tenant.getNombre(),
                tenant == null ? null : tenant.getCodigo(),
                tenant == null ? null : tenant.getLogoUrl(),
                tenant == null ? null : tenant.getBusinessType(),
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
                near
        );
    }

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