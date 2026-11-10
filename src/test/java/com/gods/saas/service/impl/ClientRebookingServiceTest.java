package com.gods.saas.service.impl;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.model.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ClientRebookingServiceTest {
 final AppointmentRepository repo=mock(AppointmentRepository.class);
 final ClientRebookingService service=new ClientRebookingService(repo);
 @Test void visitMustBelongToAuthenticatedCustomerAndBusiness(){when(repo.findByIdAndTenant_IdAndCustomer_Id(7L,1L,2L)).thenReturn(Optional.empty());assertThrows(ResponseStatusException.class,()->service.seed(1L,2L,7L));}
 @Test void retiredServiceCannotBeRebooked(){var a=new Appointment();a.setService(ServiceEntity.builder().activo(false).build());when(repo.findByIdAndTenant_IdAndCustomer_Id(7L,1L,2L)).thenReturn(Optional.of(a));assertThrows(ResponseStatusException.class,()->service.seed(1L,2L,7L));}
 @Test void seedContainsIdsOnlyAndCreatesNoBooking(){var a=new Appointment();a.setService(ServiceEntity.builder().id(3L).activo(true).build());a.setBranch(Branch.builder().id(4L).activo(true).build());when(repo.findByIdAndTenant_IdAndCustomer_Id(7L,1L,2L)).thenReturn(Optional.of(a));assertEquals(Map.of("branchId",4L,"serviceId",3L),service.seed(1L,2L,7L));verify(repo,never()).save(any());}
}
