package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CashMovementRequest;
import com.gods.saas.domain.enums.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashEmployeePaymentTest {
    @Mock CashRegisterRepository registers;
    @Mock CashMovementRepository movements;
    @Mock AppUserRepository users;
    @Mock TenantSettingsRepository settings;
    @Mock UserTenantRoleRepository roles;
    @InjectMocks CashRegisterServiceImpl service;
    AppUser employee;
    CashMovementRequest request;

    @BeforeEach void setup() {
        employee = new AppUser();
        employee.setId(7L);
        employee.setNombre("Empleado");
        employee.setActivo(true);
        employee.setSalaryMode(true);
        employee.setFixedSalaryAmount(new BigDecimal("1200"));
        employee.setSalaryFrequency(SalaryFrequency.MONTHLY);
        Branch branch = new Branch();
        branch.setId(2L);
        CashRegister register = CashRegister.builder().id(3L).branch(branch)
                .status(CashRegisterStatus.OPEN).build();
        when(settings.findByTenant_Id(1L)).thenReturn(Optional.empty());
        when(roles.existsByUserIdAndTenantIdAndRoleIn(eq(9L), eq(1L), anyList())).thenReturn(true);
        when(registers.findByIdAndTenant_Id(3L,1L)).thenReturn(Optional.of(register));
        when(registers.findForBusinessDateActivity(eq(1L),eq(2L),any(),any())).thenReturn(List.of());
        when(users.findById(9L)).thenReturn(Optional.of(new AppUser()));
        when(users.findByIdAndTenant_Id(7L,1L)).thenReturn(Optional.of(employee));
        request = new CashMovementRequest();
        request.setType(CashMovementType.PAYMENT_BARBER);
        request.setBarberUserId(7L);
        request.setAmount(new BigDecimal("100"));
        request.setFundingSource(CashFundingSource.EXTERNAL);
        request.setPaymentMethod(PaymentMethod.CASH);
    }

    @ParameterizedTest @EnumSource(value=RoleType.class, names={"ADMIN","CASHIER","BARBER"})
    void registersPaymentForEligibleEmployee(RoleType role) {
        when(roles.existsByUser_IdAndTenant_IdAndBranch_IdAndRole(eq(7L),eq(1L),eq(2L),any(RoleType.class)))
                .thenAnswer(invocation -> invocation.getArgument(3) == role);
        when(movements.save(any(CashMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.createMovement(1L,2L,3L,9L,request);
        assertEquals(7L,result.getBarberUserId());
        assertEquals(new BigDecimal("100"),result.getAmount());
        verify(movements).save(argThat(movement -> movement.getBarberUser() == employee));
    }

    @Test void rejectsEmployeeWithoutRoleInThisBranch() {
        assertThrows(IllegalStateException.class, () -> service.createMovement(1L,2L,3L,9L,request));
        verifyNoInteractions(movements);
    }

    @Test void rejectsCashierWithoutConfiguredSalary() {
        when(roles.existsByUser_IdAndTenant_IdAndBranch_IdAndRole(eq(7L),eq(1L),eq(2L),any(RoleType.class)))
                .thenAnswer(invocation -> invocation.getArgument(3) == RoleType.CASHIER);
        employee.setSalaryMode(false);
        assertThrows(IllegalStateException.class, () -> service.createMovement(1L,2L,3L,9L,request));
        verifyNoInteractions(movements);
    }

    @Test void rejectsInactiveEmployee() {
        when(roles.existsByUser_IdAndTenant_IdAndBranch_IdAndRole(eq(7L),eq(1L),eq(2L),any(RoleType.class)))
                .thenAnswer(invocation -> invocation.getArgument(3) == RoleType.ADMIN);
        employee.setActivo(false);
        assertThrows(IllegalStateException.class, () -> service.createMovement(1L,2L,3L,9L,request));
        verifyNoInteractions(movements);
    }
}
