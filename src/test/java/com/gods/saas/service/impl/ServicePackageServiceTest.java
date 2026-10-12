package com.gods.saas.service.impl;

import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicePackageServiceTest {
    private final ServiceRepository repository = mock(ServiceRepository.class);
    private final ServicePackageService packages = new ServicePackageService(repository);
    private ServiceEntity component(long id) {
        var service = ServiceEntity.builder().id(id).activo(true).precio(30.0).build();
        when(repository.findByIdAndTenant_IdAndDeletedAtIsNull(id, 1L)).thenReturn(Optional.of(service));
        return service;
    }
    @Test void createsFixedPricePackageAndPreservesCompositionForOlderClients() {
        component(2); component(3);
        var target = ServiceEntity.builder().id(10L).precio(45.0).activo(true).build();
        packages.configure(1L, target, List.of(2L, 3L), false);
        packages.configure(1L, target, null, false);
        assertEquals(List.of(2L, 3L), target.getPackageServiceIds());
        assertEquals(new BigDecimal("45.00"), packages.salePrice(1L,target,45.0));
        assertEquals(new BigDecimal("45.00"), packages.salePrice(1L,target,null));
        assertThrows(IllegalArgumentException.class, () -> packages.salePrice(1L,target,1.0));
        target.setActivo(false);
        assertThrows(IllegalArgumentException.class, () -> packages.salePrice(1L,target,45.0));
    }
    @Test void rejectsForeignInactiveNestedAndSelfComponents() {
        var a=component(2); component(3);
        var target=ServiceEntity.builder().id(10L).build();
        assertThrows(IllegalArgumentException.class, () -> packages.configure(1L,target,List.of(2L,999L),false));
        a.setActivo(false);
        assertThrows(IllegalArgumentException.class, () -> packages.configure(1L,target,List.of(2L,3L),false));
        a.setActivo(true); a.setPackageServiceIds(List.of(7L,8L));
        assertThrows(IllegalArgumentException.class, () -> packages.configure(1L,target,List.of(2L,3L),false));
        assertThrows(IllegalArgumentException.class, () -> packages.configure(1L,target,List.of(10L,3L),false));
    }
    @Test void rejectsInvalidCompositionAndVariablePrice() {
        var target=new ServiceEntity();
        assertThrows(IllegalArgumentException.class, () -> packages.configure(1L,target,List.of(2L),false));
        assertThrows(IllegalArgumentException.class, () -> packages.configure(1L,target,List.of(2L,2L),false));
        assertThrows(IllegalArgumentException.class, () -> packages.configure(1L,target,java.util.Arrays.asList(2L,null),false));
        assertThrows(IllegalArgumentException.class, () -> packages.configure(1L,target,List.of(2L,3L),true));
    }
    @Test void preventsDeletingOrDeactivatingIncludedService() {
        var target=ServiceEntity.builder().id(10L).packageServiceIds(List.of(2L,3L)).build();
        when(repository.findByTenant_IdAndDeletedAtIsNullOrderByNombreAsc(1L)).thenReturn(List.of(target));
        assertThrows(IllegalArgumentException.class, () -> packages.requireNotIncluded(1L,2L));
        assertDoesNotThrow(() -> packages.requireNotIncluded(1L,10L));
    }
}
