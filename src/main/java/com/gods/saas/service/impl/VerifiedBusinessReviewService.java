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
            row.put("ownerReply", item.getOwnerReply());
            row.put("ownerRepliedAt", item.getOwnerRepliedAt());
            row.put("moderationStatus", item.getModerationStatus());
            row.put("reportReason", item.getReportReason());
            row.put("reportDetails", item.getReportDetails());
            row.put("reportedAt", item.getReportedAt());
            return row;
        }).toList();
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("average", Math.round(average * 10.0) / 10.0);
        result.put("total", all.size());
        result.put("distribution", distribution);
        result.put("reviews", rows);
        return result;
    }
    @Transactional
    public java.util.Map<String, Object> reply(Long tenantId, Long actorUserId, Long reviewId, String rawReply) {
        VerifiedBusinessReview review = reviewRepository.findByIdAndTenant_Id(reviewId, tenantId)
                .orElseThrow(() -> new BusinessException("Reseña no encontrada"));
        String reply = rawReply == null ? "" : rawReply.trim();
        if (reply.isEmpty()) throw new BusinessException("Escribe una respuesta");
        if (reply.length() > 500) throw new BusinessException("La respuesta no puede superar 500 caracteres");
        review.setOwnerReply(reply);
        review.setOwnerRepliedAt(java.time.LocalDateTime.now());
        review.setOwnerRepliedByUserId(actorUserId);
        reviewRepository.save(review);

        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("reviewId", review.getId());
        result.put("ownerReply", review.getOwnerReply());
        result.put("ownerRepliedAt", review.getOwnerRepliedAt());
        return result;
    }
    @Transactional
    public java.util.Map<String, Object> report(Long tenantId, Long actorUserId, Long reviewId,
                                                String rawReason, String rawDetails) {
        VerifiedBusinessReview review = reviewRepository.findByIdAndTenant_Id(reviewId, tenantId)
                .orElseThrow(() -> new BusinessException("Reseña no encontrada"));
        if ("HIDDEN".equalsIgnoreCase(review.getModerationStatus())) {
            throw new BusinessException("Esta reseña ya fue moderada y solo Super Gods puede restaurarla");
        }
        String reason = rawReason == null ? "" : rawReason.trim().toUpperCase();
        Set<String> allowed = Set.of("OFFENSIVE", "PERSONAL_DATA", "FALSE_CONTENT", "SPAM", "OTHER");
        if (!allowed.contains(reason)) throw new BusinessException("Motivo de reporte no válido");
        String details = rawDetails == null || rawDetails.trim().isEmpty() ? null : rawDetails.trim();
        review.setReportReason(reason);
        review.setReportDetails(details);
        review.setReportedAt(java.time.LocalDateTime.now());
        review.setReportedByUserId(actorUserId);
        review.setModerationStatus("PENDING_REVIEW");
        reviewRepository.save(review);
        return moderationResult(review);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> moderationInbox(String status) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        var reviews = reviewRepository.findTop500ByOrderByCreatedAtDesc().stream()
                .filter(item -> normalized == null || normalized.equals(item.getModerationStatus()))
                .filter(item -> normalized != null || item.getReportedAt() != null)
                .map(this::moderationRow)
                .toList();
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("total", reviews.size());
        result.put("reviews", reviews);
        return result;
    }

    @Transactional
    public java.util.Map<String, Object> moderate(Long actorUserId, Long reviewId,
                                                  String rawStatus, String rawNote) {
        VerifiedBusinessReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("Reseña no encontrada"));
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase();
        if (!Set.of("PUBLISHED", "HIDDEN").contains(status)) {
            throw new BusinessException("Decisión de moderación no válida");
        }
        String note = rawNote == null ? "" : rawNote.trim();
        if (note.isEmpty()) throw new BusinessException("Explica la decisión");
        if (note.length() > 500) throw new BusinessException("La nota no puede superar 500 caracteres");
        review.setModerationStatus(status);
        review.setModerationNote(note);
        review.setModeratedAt(java.time.LocalDateTime.now());
        review.setModeratedByUserId(actorUserId);
        reviewRepository.save(review);
        return moderationResult(review);
    }

    private java.util.Map<String, Object> moderationRow(VerifiedBusinessReview item) {
        var row = new java.util.LinkedHashMap<String, Object>();
        row.put("reviewId", item.getId());
        row.put("tenantId", item.getTenant().getId());
        row.put("tenantName", item.getTenant().getNombre());
        row.put("branchId", item.getBranch().getId());
        row.put("branchName", item.getBranch().getNombre());
        row.put("customerName", item.getCustomer().getNombres());
        row.put("rating", item.getRating());
        row.put("comment", item.getComment());
        row.put("createdAt", item.getCreatedAt());
        row.put("ownerReply", item.getOwnerReply());
        row.put("moderationStatus", item.getModerationStatus());
        row.put("reportReason", item.getReportReason());
        row.put("reportDetails", item.getReportDetails());
        row.put("reportedAt", item.getReportedAt());
        row.put("moderationNote", item.getModerationNote());
        row.put("moderatedAt", item.getModeratedAt());
        return row;
    }

    private java.util.Map<String, Object> moderationResult(VerifiedBusinessReview item) {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("reviewId", item.getId());
        result.put("moderationStatus", item.getModerationStatus());
        result.put("reportReason", item.getReportReason());
        result.put("reportDetails", item.getReportDetails());
        result.put("reportedAt", item.getReportedAt());
        result.put("moderationNote", item.getModerationNote());
        result.put("moderatedAt", item.getModeratedAt());
        return result;
    }}