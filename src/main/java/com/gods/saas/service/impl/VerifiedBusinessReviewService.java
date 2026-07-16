package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateVerifiedReviewRequest;
import com.gods.saas.domain.dto.response.VerifiedReviewResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.VerifiedBusinessReview;
import com.gods.saas.domain.repository.AppointmentRepository;
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
    private final VerifiedBusinessReviewRepository reviewRepository;

    @Transactional
    public VerifiedReviewResponse create(Long tenantId, Long customerId, CreateVerifiedReviewRequest request) {
        Appointment appointment = appointmentRepository
                .findByIdAndTenant_IdAndCustomer_Id(request.appointmentId(), tenantId, customerId)
                .orElseThrow(() -> new BusinessException("Atencion no encontrada"));
        String status = appointment.getEstado() == null ? "" : appointment.getEstado().trim().toUpperCase();
        if (!COMPLETED.contains(status)) throw new BusinessException("Solo puedes calificar una atencion completada");
        if (reviewRepository.existsByAppointment_Id(appointment.getId())) throw new BusinessException("Esta atencion ya fue calificada");
        String comment = request.comment() == null || request.comment().trim().isEmpty() ? null : request.comment().trim();
        VerifiedBusinessReview saved = reviewRepository.save(VerifiedBusinessReview.builder()
                .tenant(appointment.getTenant()).branch(appointment.getBranch()).customer(appointment.getCustomer())
                .appointment(appointment).rating(request.rating()).comment(comment).build());
        return toResponse(saved);
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
        return new VerifiedReviewResponse(item.getId(), item.getAppointment().getId(), item.getBranch().getId(),
                item.getRating(), item.getComment(), name, item.getCreatedAt());
    }
}