package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateVerifiedReviewRequest;
import com.gods.saas.domain.dto.response.VerifiedReviewResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.model.VerifiedBusinessReview;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.VerifiedBusinessReviewRepository;
import com.gods.saas.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service @RequiredArgsConstructor
public class VerifiedBusinessReviewService {
    private static final Set<String> COMPLETED = Set.of("ATENDIDO", "COMPLETADO", "COMPLETADA", "COMPLETED", "FINALIZADO", "FINALIZADA");
    private final AppointmentRepository appointmentRepository;
    private final SaleRepository saleRepository;
    private final VerifiedBusinessReviewRepository reviewRepository;

    @Transactional
    public VerifiedReviewResponse create(Long tenantId, Long customerId, CreateVerifiedReviewRequest request) {
        if (request.appointmentId() == null && request.saleId() == null) {
            throw new BusinessException("Debes indicar la visita que deseas calificar");
        }
        if (request.appointmentId() != null) {
            return createForAppointment(tenantId, customerId, request);
        }
        return createForDirectSale(tenantId, customerId, request);
    }

    private VerifiedReviewResponse createForAppointment(Long tenantId, Long customerId,
                                                         CreateVerifiedReviewRequest request) {
        Appointment appointment = appointmentRepository
                .findByIdAndTenant_Id(request.appointmentId(), tenantId)
                .orElseThrow(() -> new BusinessException("Atención no encontrada"));
        boolean belongsToCustomer = appointment.getCustomer() != null
                && appointment.getCustomer().getId().equals(customerId);
        boolean linkedToCustomerSale = saleRepository.existsReviewableVisit(
                tenantId, customerId, appointment.getId());
        if (!belongsToCustomer && !linkedToCustomerSale) {
            throw new BusinessException("Esta visita no pertenece a tu cuenta");
        }
        String status = appointment.getEstado() == null ? "" : appointment.getEstado().trim().toUpperCase();
        if (!COMPLETED.contains(status)) throw new BusinessException("Solo puedes calificar una atención completada");
        if (reviewRepository.existsByAppointment_Id(appointment.getId())) {
            throw new BusinessException("Esta atención ya fue calificada");
        }
        VerifiedBusinessReview saved = reviewRepository.save(baseReview(request)
                .tenant(appointment.getTenant())
                .branch(appointment.getBranch())
                .customer(appointment.getCustomer() != null ? appointment.getCustomer() :
                        saleRepository.findByIdAndTenant_IdAndCustomer_Id(request.saleId(), tenantId, customerId)
                                .map(Sale::getCustomer).orElse(null))
                .appointment(appointment)
                .build());
        return toResponse(saved);
    }

    private VerifiedReviewResponse createForDirectSale(Long tenantId, Long customerId,
                                                        CreateVerifiedReviewRequest request) {
        Sale sale = saleRepository.findByIdAndTenant_IdAndCustomer_Id(request.saleId(), tenantId, customerId)
                .orElseThrow(() -> new BusinessException("Venta no encontrada"));
        if (sale.getPaymentValidationStatus() != null
                && !"APPROVED".equalsIgnoreCase(sale.getPaymentValidationStatus())) {
            throw new BusinessException("La venta aún no está aprobada");
        }
        if (reviewRepository.existsBySale_Id(sale.getId())) {
            throw new BusinessException("Esta visita ya fue calificada");
        }
        VerifiedBusinessReview saved = reviewRepository.save(baseReview(request)
                .tenant(sale.getTenant())
                .branch(sale.getBranch())
                .customer(sale.getCustomer())
                .sale(sale)
                .build());
        return toResponse(saved);
    }

    private VerifiedBusinessReview.VerifiedBusinessReviewBuilder baseReview(CreateVerifiedReviewRequest request) {
        String comment = request.comment() == null || request.comment().trim().isEmpty()
                ? null : request.comment().trim();
        return VerifiedBusinessReview.builder().rating(request.rating()).comment(comment);
    }

    @Transactional(readOnly = true)
    public VerifiedReviewResponse find(Long tenantId, Long customerId, Long appointmentId) {
        VerifiedBusinessReview review = reviewRepository.findByAppointment_IdAndCustomer_Id(appointmentId, customerId)
                .filter(item -> item.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new BusinessException("Calificacion no encontrada"));
        return toResponse(review);
    }

    private VerifiedReviewResponse toResponse(VerifiedBusinessReview item) {
        String name = item.getCustomer().getNombres();
        return new VerifiedReviewResponse(item.getId(),
                item.getAppointment() != null ? item.getAppointment().getId() : null,
                item.getSale() != null ? item.getSale().getId() : null, item.getBranch().getId(),
                item.getRating(), item.getComment(), name, item.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> ownerInbox(Long tenantId, Long branchId, Integer rating) {
        var all = reviewRepository.findTop200ByTenant_IdOrderByCreatedAtDesc(tenantId);
        var filtered = all.stream()
                .filter(item -> branchId == null || item.getBranch().getId().equals(branchId))
                .filter(item -> rating == null || item.getRating().equals(rating))
                .toList();
        double average = all.stream().mapToInt(VerifiedBusinessReview::getRating).average().orElse(0.0);
        var distribution = new java.util.LinkedHashMap<String, Long>();
        for (int stars = 5; stars >= 1; stars--) {
            final int value = stars;
            distribution.put(String.valueOf(stars), all.stream().filter(item -> item.getRating() == value).count());
        }
        var rows = filtered.stream().map(item -> {
            var row = new java.util.LinkedHashMap<String, Object>();
            row.put("reviewId", item.getId());
            row.put("appointmentId", item.getAppointment() == null ? null : item.getAppointment().getId());
            row.put("saleId", item.getSale() == null ? null : item.getSale().getId());
            row.put("branchId", item.getBranch().getId());
            row.put("branchName", item.getBranch().getNombre());
            row.put("rating", item.getRating());
            row.put("comment", item.getComment());
            row.put("customerName", item.getCustomer().getNombres());
            row.put("createdAt", item.getCreatedAt());
            row.put("verified", true);
            return row;
        }).toList();
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("average", Math.round(average * 10.0) / 10.0);
        result.put("total", all.size());
        result.put("distribution", distribution);
        result.put("reviews", rows);
        return result;
    }}