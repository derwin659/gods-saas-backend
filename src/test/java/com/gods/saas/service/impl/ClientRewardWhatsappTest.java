package com.gods.saas.service.impl;

import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientRewardWhatsappTest {
    final CustomerRepository customers=mock(CustomerRepository.class);
    final LoyaltyAccountRepository accounts=mock(LoyaltyAccountRepository.class);
    final RewardItemRepository rewards=mock(RewardItemRepository.class);
    final RewardRedemptionRepository redemptions=mock(RewardRedemptionRepository.class);
    final LoyaltyMovementRepository movements=mock(LoyaltyMovementRepository.class);
    final LoyaltyLotConsumptionService lots=mock(LoyaltyLotConsumptionService.class);
    final NotificationService notices=mock(NotificationService.class);
    final ClientRewardService service=new ClientRewardService(customers,accounts,rewards,redemptions,movements,lots,notices);
    LoyaltyAccount account;RewardItem reward;Customer customer;
    @BeforeEach void setup() {
        customer=Customer.builder().id(7L).tenant(new Tenant(2L)).telefono("+51999999999").phoneVerified(true).whatsappTransactionalEnabled(true).build();
        account=LoyaltyAccount.builder().id(9L).puntosDisponibles(100).build();
        reward=RewardItem.builder().id(3L).nombre("Corte gratis").puntosRequeridos(60).stock(1).build();
        when(customers.findByIdAndTenantIdWithTenant(7L,2L)).thenReturn(Optional.of(customer));
        when(accounts.findForRedemption(2L,7L)).thenReturn(Optional.of(account));
        when(rewards.findForRedemption(3L,2L)).thenReturn(Optional.of(reward));
        when(redemptions.save(any())).thenAnswer(i->{RewardRedemption r=i.getArgument(0);r.setId(11L);return r;});
    }
    @Test void confirmedCanjeUsesSharedAccountingAndGeneratesPickupCode() {
        var result=service.redeemForWhatsapp(2L,7L,3L,60);
        assertEquals(40,result.getPuntosDisponibles());assertTrue(result.getCodigo().startsWith("GODS-"));
        assertEquals(0,reward.getStock());verify(lots).consume(2L,7L,60);
        verify(accounts).save(account);verify(rewards).save(reward);verify(movements).save(argThat(m->m.getPuntos()==-60&&m.getSaldoResultante()==40&&m.getTenantId()==2L));
        verify(redemptions).save(argThat(r->r.getEstado().equals("GENERATED")&&r.getCustomerId()==7L&&r.getTenantId()==2L));
        var order=inOrder(accounts,rewards);order.verify(accounts).findForRedemption(2L,7L);order.verify(rewards).findForRedemption(3L,2L);
    }
    @Test void changedCostNeverDebitsOrConsumesStock() {reward.setPuntosRequeridos(70);reject(60);}
    @Test void insufficientBalanceNeverDebitsOrConsumesStock() {account.setPuntosDisponibles(10);reject(60);}
    @Test void exhaustedStockNeverDebitsOrConsumesPoints() {reward.setStock(0);reject(60);}
    @Test void unverifiedCustomerCannotRedeem() {customer.setPhoneVerified(false);reject(60);verifyNoInteractions(accounts,rewards);}
    @Test void wrongTenantCannotReadOrRedeem() {assertThrows(ClientRewardService.RewardUnavailableException.class,()->service.redeemForWhatsapp(99L,7L,3L,60));verifyNoInteractions(accounts,rewards);}
    private void reject(int expected) {
        assertThrows(ClientRewardService.RewardUnavailableException.class,()->service.redeemForWhatsapp(2L,7L,3L,expected));
        verify(accounts,never()).save(any());verify(rewards,never()).save(any());verifyNoInteractions(lots,redemptions,movements,notices);
    }
}
