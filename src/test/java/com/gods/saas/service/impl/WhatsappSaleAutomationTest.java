package com.gods.saas.service.impl;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class WhatsappSaleAutomationTest {
 @Mock SaleRepository sales;
 @Mock AppUserRepository users;
 @Mock TenantSettingsRepository settings;
 @Mock LoyaltyAccountRepository loyaltyAccounts;
 @Mock TenantTimeService time;
 @Mock AdminPermissionService permissions;
 @Mock NotificationService notifications;
 @Mock WhatsappBusinessSenderService senders;
 @Mock PostSaleWhatsappMessageFactory postSaleMessages;
 @InjectMocks CashSaleServiceImpl service;
 Sale sale; TenantSettings config;
 @BeforeEach void setup() {
   var tenant = new Tenant(1L); tenant.setNombre("Barberia");
   var customer = new Customer(); customer.setId(4L); customer.setNombres("Ana"); customer.setTelefono("+51958062847");
   sale = Sale.builder().id(12L).tenant(tenant).customer(customer).total(new BigDecimal("35.00")).paymentValidationStatus("PENDING_VALIDATION").createdByRole("BARBER").build();
   lenient().when(sales.findForPaymentApproval(12L,1L,2L)).thenReturn(Optional.of(sale));
 }
 void approveSetup() {
   when(users.findById(anyLong())).thenAnswer(i -> { var u=new AppUser();u.setId(i.getArgument(0));return Optional.of(u); });
   when(sales.save(sale)).thenReturn(sale);
   when(time.now(1L)).thenReturn(LocalDateTime.of(2026,11,3,12,0));
   config=new TenantSettings();config.setScheduleConfig(new HashMap<>(Map.of("whatsappIncludeAppDownloadLink",false,"whatsappIncludeBookingLink",false)));
   when(settings.findByTenant_Id(1L)).thenReturn(Optional.of(config));
   when(postSaleMessages.forSale(eq(sale),anyInt())).thenAnswer(i -> sale.getWhatsappReceiptSnapshot() != null ? PostSaleWhatsappMessageFactory.restore(sale.getWhatsappReceiptSnapshot()) : PostSaleWhatsappMessageFactory.compose("Ana","Barberia",100,0,100,"","","",""));
   when(senders.selection(eq(1L),anyMap())).thenReturn(new WhatsappBusinessSenderService.Selection("BUSINESS","+51911111111","CONNECTED","BUSINESS","+51911111111"));
 }
 @ParameterizedTest @ValueSource(longs={8L,9L})
 void eitherAuthorizedApproverQueuesReceiptAndDoesNotOpenManualWhatsapp(Long approver) {
   approveSetup(); var response=service.approveSalePayment(1L,2L,approver,12L);
   assertEquals("APPROVED",sale.getPaymentValidationStatus()); assertEquals(approver,sale.getValidatedByUser().getId());
   assertNull(response.getCustomerWhatsappUrl()); assertTrue(response.getCustomerWhatsappAutomatic()); assertNull(response.getCustomerWhatsappMessage());
   verify(permissions).checkPermission("CASH_APPROVE_SALES");
   verify(notifications).notifySaleReceipt(eq(sale),contains("Ana"),eq(false));
   assertThrows(RuntimeException.class,()->service.approveSalePayment(1L,2L,approver,12L));
   verify(notifications,times(1)).notifySaleReceipt(any(),any(),anyBoolean());
 }
 @Test void disabledPostSaleDoesNotQueueMessage() {
   approveSetup();config.getScheduleConfig().put("whatsappPostSaleMessageEnabled",false);
   service.approveSalePayment(1L,2L,8L,12L);verifyNoInteractions(notifications);
 }
 @Test void deniedApprovalDoesNotSendOrChangeSale() {
   doThrow(new IllegalStateException("Forbidden")).when(permissions).checkPermission("CASH_APPROVE_SALES");
   assertThrows(IllegalStateException.class,()->service.approveSalePayment(1L,2L,8L,12L));
   assertEquals("PENDING_VALIDATION",sale.getPaymentValidationStatus());verifyNoInteractions(notifications);
 }
}
