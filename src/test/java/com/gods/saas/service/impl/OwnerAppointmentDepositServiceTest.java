package com.gods.saas.service.impl;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.TenantPaymentMethod;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.dto.request.ValidateAppointmentDepositRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OwnerAppointmentDepositServiceTest {
    private final AppointmentRepository repo = mock(AppointmentRepository.class);
    private final WhatsappDepositService whatsapp = mock(WhatsappDepositService.class);
    private final OwnerAppointmentDepositService service = new OwnerAppointmentDepositService(repo,whatsapp);
    private Appointment booking(boolean evidenceRequired) {
        var a = new Appointment(); a.setId(12L); a.setDepositRequired(true); a.setDepositStatus("PENDING_VALIDATION");
        var m = new TenantPaymentMethod(); m.setRequiresEvidence(evidenceRequired); m.setRequiresOperationCode(false); a.setDepositPaymentMethod(m);
        when(repo.findByIdAndTenant_Id(12L,7L)).thenReturn(Optional.of(a));
        when(repo.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0)); return a;
    }
    private ValidateAppointmentDepositRequest approve() { var r = new ValidateAppointmentDepositRequest(); r.setApproved(true); return r; }
    @Test void permitsVerifiedPaymentWithoutOptionalProof() {
        var a=booking(false); service.validateDeposit(7L,3L,12L,approve()); assertEquals("PAID",a.getDepositStatus()); assertEquals("CONFIRMED",a.getEstado());
    }
    @Test void requiredProofCannotBeBypassed() {
        booking(true); assertThrows(IllegalStateException.class,()->service.validateDeposit(7L,3L,12L,approve())); verify(repo,never()).save(any());
    }
    @Test void requiredOperationCanBeRecordedWhenApproving() {
        var a=booking(false); a.getDepositPaymentMethod().setRequiresOperationCode(true);
        var r=approve(); r.setOperationCode("12345678"); service.validateDeposit(7L,3L,12L,r);
        assertEquals("12345678",a.getDepositOperationCode()); assertEquals("PAID",a.getDepositStatus());
    }
    @Test void cannotApproveAnotherTenantsBooking() {
        when(repo.findByIdAndTenant_Id(12L,7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,()->service.validateDeposit(7L,3L,12L,approve())); verifyNoInteractions(whatsapp); verify(repo,never()).save(any());
    }
}
