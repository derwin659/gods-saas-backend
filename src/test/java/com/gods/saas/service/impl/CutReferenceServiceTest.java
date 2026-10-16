package com.gods.saas.service.impl;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutReferenceServiceTest {
    private final SaleRepository sales=mock(SaleRepository.class);
    private final CutReferenceRepository references=mock(CutReferenceRepository.class);
    private final CloudinaryStorageService storage=mock(CloudinaryStorageService.class);
    private final CutReferenceService service=new CutReferenceService(sales,references,storage);
    private Sale sale() {
        var customer=new Customer();customer.setId(7L);
        var barber=new AppUser();barber.setId(8L);
        var item=new SaleItem();item.setService(new ServiceEntity());item.setBarberUser(barber);
        var sale=new Sale();sale.setId(5L);sale.setCustomer(customer);sale.setItems(List.of(item));sale.setUser(barber);
        when(sales.findByIdAndTenant_Id(5L,1L)).thenReturn(Optional.of(sale));
        return sale;
    }
    @Test void restrictsCustomerToOwnSaleAndNeverAllowsCustomerUpload() {
        sale();
        var client=new CutReferenceService.Actor(1L,null,7L,"CUSTOMER");
        assertNull(service.get(client,5L));
        assertThrows(ResponseStatusException.class,()->service.upload(client,5L,null));
        assertThrows(ResponseStatusException.class,()->service.get(new CutReferenceService.Actor(1L,null,9L,"CUSTOMER"),5L));
        assertThrows(ResponseStatusException.class,()->service.get(new CutReferenceService.Actor(2L,null,7L,"CUSTOMER"),5L));
        verifyNoInteractions(storage);
    }
    @Test void restrictsProfessionalAndHandlesOldVisitsWithoutMedia() {
        sale();
        assertNull(service.get(new CutReferenceService.Actor(1L,8L,null,"BARBER"),5L));
        assertThrows(ResponseStatusException.class,()->service.get(new CutReferenceService.Actor(1L,9L,null,"BARBER"),5L));
        verifyNoInteractions(storage);
    }
    @Test void uploadAttachesOnceAndReattemptDoesNotCreateDuplicate() {
        sale();var actor=new CutReferenceService.Actor(1L,8L,null,"BARBER");
        var file=new MockMultipartFile("file","cut.jpg","image/jpeg",new byte[]{1});
        when(storage.uploadCutReference(1L,8L,file)).thenReturn(new CloudinaryStorageService.CutUpload("private-id","IMAGE","jpg"));
        service.upload(actor,5L,file);
        var capture=org.mockito.ArgumentCaptor.forClass(CutReference.class);verify(references).saveAndFlush(capture.capture());
        assertEquals(1L,capture.getValue().getTenantId());assertEquals(5L,capture.getValue().getSaleId());
        when(references.findByTenantIdAndSaleId(1L,5L)).thenReturn(Optional.of(capture.getValue()));
        service.upload(actor,5L,file);
        verify(storage,times(1)).uploadCutReference(any(),any(),any());
    }
    @Test void cleansUploadedAssetWhenPersistenceFails() {
        sale();var file=new MockMultipartFile("file",new byte[]{1});
        when(storage.uploadCutReference(1L,8L,file)).thenReturn(new CloudinaryStorageService.CutUpload("private-id","VIDEO","mp4"));
        when(references.saveAndFlush(any())).thenThrow(new IllegalStateException("database"));
        assertThrows(IllegalStateException.class,()->service.upload(new CutReferenceService.Actor(1L,8L,null,"BARBER"),5L,file));
        verify(storage).deleteCutReference("private-id","VIDEO");
    }
    @Test void rejectsNonMediaBeforeContactingProvider() {
        var realStorage=new CloudinaryStorageService("demo","key","secret",mock(ShowcaseUploadRateLimitService.class));
        assertThrows(IllegalArgumentException.class,()->realStorage.uploadCutReference(1L,8L,new MockMultipartFile("file","bad.txt","text/plain",new byte[]{1,2})));
        assertThrows(IllegalArgumentException.class,()->realStorage.uploadCutReference(1L,8L,new MockMultipartFile("file","fake.mp4","video/mp4",new byte[]{1,2,3,4})));
    }
}
