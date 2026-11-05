package com.gods.saas.service.impl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BookingDepositEvidenceServiceTest {
    private final CloudinaryStorageService storage = mock(CloudinaryStorageService.class);
    private final BookingDepositEvidenceService service = new BookingDepositEvidenceService(storage);
    // Valid PNG signature; the storage provider validates the complete image.
    private final String png = "data:image/png;base64,iVBORw0KGgo=";
    @AfterEach void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    @Test void uploadsInlineProofAndStoresOnlyUrl() {
        when(storage.uploadAppointmentDepositEvidenceBytes(eq(7L),eq(9L),any(byte[].class)))
            .thenReturn(new CloudinaryStorageService.UploadResult("https://res.cloudinary.com/proof.png", "proof"));
        assertEquals("https://res.cloudinary.com/proof.png",service.prepare(7L,9L,png));
        verify(storage).uploadAppointmentDepositEvidenceBytes(eq(7L),eq(9L),any(byte[].class));
    }
    @Test void removesUploadedProofIfBookingRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        when(storage.uploadAppointmentDepositEvidenceBytes(eq(7L),eq(9L),any(byte[].class)))
            .thenReturn(new CloudinaryStorageService.UploadResult("https://res.cloudinary.com/proof.png", "proof"));
        service.prepare(7L,9L,png);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).deleteImage("proof");
    }
    @Test void rejectsDisguisedOrBrokenFilesBeforeUpload() {
        assertThrows(IllegalArgumentException.class,()->service.prepare(7L,9L,"data:image/png;base64,aGVsbG8="));
        assertThrows(IllegalArgumentException.class,()->service.prepare(7L,9L,"data:image/svg+xml;base64,aGVsbG8="));
        assertThrows(IllegalArgumentException.class,()->service.prepare(7L,9L,"data:image/png;base64,INVALID!"));
        verifyNoInteractions(storage);
    }
    @Test void preservesExistingUploadedProofWithoutFetchingIt() {
        assertEquals("https://example.com/old.png",service.prepare(7L,9L,"https://example.com/old.png"));
        assertNull(service.prepare(7L,9L," "));
        verifyNoInteractions(storage);
    }
}
