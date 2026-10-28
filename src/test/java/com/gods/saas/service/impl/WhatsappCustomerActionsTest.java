package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.dto.response.RedeemRewardResponse;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsappCustomerActionsTest {
    final RewardItemRepository rewards=mock(RewardItemRepository.class);
    final RewardRedemptionRepository redemptions=mock(RewardRedemptionRepository.class);
    final LoyaltyAccountRepository loyalty=mock(LoyaltyAccountRepository.class);
    final AppointmentRepository appointments=mock(AppointmentRepository.class);
    final ClientRewardService redemption=mock(ClientRewardService.class);
    final TenantTimeService time=mock(TenantTimeService.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ObjectMapper mapper=new ObjectMapper();
    final WhatsappCustomerActions service=new WhatsappCustomerActions(rewards,redemptions,loyalty,appointments,redemption,time,jdbc,mapper);
    ObjectNode root;RewardItem reward;
    @BeforeEach void setup() {
        root=mapper.createObjectNode();root.set("_assistant",mapper.createObjectNode().put("stage","MENU"));
        reward=RewardItem.builder().id(3L).nombre("Corte gratis").puntosRequeridos(60).stock(1).build();
        when(rewards.findByTenant_IdAndActivoTrueOrderByPuntosRequeridosAsc(2L)).thenReturn(List.of(reward));
        when(rewards.findByIdAndTenant_IdAndActivoTrue(3L,2L)).thenReturn(Optional.of(reward));
        when(loyalty.findByTenant_IdAndCustomer_Id(2L,7L)).thenReturn(Optional.of(LoyaltyAccount.builder().puntosDisponibles(100).build()));
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(i->{root=(ObjectNode)mapper.readTree(i.getArgument(1).toString());return 1;});
        when(redemption.redeemForWhatsapp(2L,7L,3L,60)).thenReturn(new RedeemRewardResponse(true,"ok",40,"GODS-ABC123",5L));
        when(time.today(2L)).thenReturn(LocalDate.of(2026,10,28));
    }
    String reply(String text) throws Exception {return service.reply("+51999999999",2L,7L,text,root);}
    void summary() throws Exception {assertTrue(reply("7").contains("Corte gratis"));assertTrue(reply("1").contains("¿Confirmas"));}
    @Test void rewardsRequireExplicitConfirmationAndSecondYesCannotRedeemAgain() throws Exception {
        summary();verifyNoInteractions(redemption);
        assertTrue(reply("SI").contains("GODS-ABC123"));
        assertNull(reply("SI"));verify(redemption,times(1)).redeemForWhatsapp(2L,7L,3L,60);
    }
    @Test void rejectionDoesNotSpendPoints() throws Exception {summary();assertTrue(reply("NO").contains("cancelado"));verifyNoInteractions(redemption);}
    @Test void insufficientPointsExplainsMissingBalance() throws Exception {
        when(loyalty.findByTenant_IdAndCustomer_Id(2L,7L)).thenReturn(Optional.of(LoyaltyAccount.builder().puntosDisponibles(10).build()));
        reply("premios");assertTrue(reply("1").contains("Te faltan 50"));verifyNoInteractions(redemption);
    }
    @Test void stockOrCostChangedReturnsControlledFailureAndResetsConfirmation() throws Exception {
        summary();when(redemption.redeemForWhatsapp(2L,7L,3L,60)).thenThrow(new ClientRewardService.RewardUnavailableException("El stock cambio."));
        assertTrue(reply("SI").contains("No realizamos el canje"));assertNull(reply("SI"));
    }
    @Test void myRedemptionsOnlyReadsCurrentTenantAndCustomerWithoutSpending() throws Exception {
        when(redemptions.findByTenantIdAndCustomerIdOrderByFechaCreacionDesc(2L,7L)).thenReturn(List.of(RewardRedemption.builder().codigo("GODS-ABC123").puntosUsados(60).estado("GENERATED").build()));
        assertTrue(reply("8").contains("pendiente de entrega"));verifyNoInteractions(redemption);
    }
    @Test void appointmentsOnlyReadVerifiedCustomersBusiness() throws Exception {
        when(appointments.findUpcomingForCustomer(eq(2L),eq(7L),any(),any())).thenReturn(List.of(Appointment.builder().id(12L).fecha(LocalDate.of(2026,10,29)).estado("CONFIRMED").build()));
        assertTrue(reply("9").contains("#12"));verifyNoInteractions(redemption);
    }
    @Test void numericSelectionsOutsideRewardsDoNotTriggerCanje() throws Exception {assertNull(reply("1"));assertNull(reply("SI"));verifyNoInteractions(redemption);}
}
