package com.gods.saas.invoicing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.dto.request.IssueElectronicDocumentRequest;
import com.gods.saas.domain.model.ElectronicInvoicingSettings;
import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.model.SaleItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MifactSalePayloadFactoryTest {
    private final MifactSalePayloadFactory factory = new MifactSalePayloadFactory(new ObjectMapper());

    @Test
    void mapsDiscountAndExcludesTipWhileKeepingTaxTotalsExact() {
        ElectronicInvoicingSettings settings = settings();
        SaleItem first = SaleItem.builder().id(10L).nombreItem("Corte").cantidad(1)
                .subtotal(new BigDecimal("100.00")).build();
        SaleItem second = SaleItem.builder().id(11L).nombreItem("Producto").cantidad(1)
                .subtotal(new BigDecimal("50.00")).build();
        Sale sale = Sale.builder().id(5L).subtotal(new BigDecimal("150.00"))
                .discount(new BigDecimal("15.00")).tipAmount(new BigDecimal("10.00"))
                .total(new BigDecimal("145.00")).saleDate(LocalDateTime.of(2026, 8, 17, 12, 0))
                .items(List.of(first, second)).build();
        IssueElectronicDocumentRequest request = new IssueElectronicDocumentRequest(
                1L, ElectronicDocumentType.RECEIPT, "1", "40506089",
                "Maria Gonzales", "Cusco", "cliente@example.com");

        ObjectNode payload = factory.build(sale, settings, request, "secret", "B001", 7);

        assertThat(payload.path("MNT_TOT").asText()).isEqualTo("135.00");
        assertThat(new BigDecimal(payload.path("MNT_TOT_GRAVADO").asText())
                .add(new BigDecimal(payload.path("MNT_TOT_TRIB_IGV").asText())))
                .isEqualByComparingTo("135.00");
        assertThat(payload.path("NUM_CORRE_CPE").asText()).isEqualTo("00000007");
        assertThat(payload.path("items").size()).isEqualTo(2);
    }

    @Test
    void rejectsInvoiceWithoutRuc() {
        SaleItem item = SaleItem.builder().nombreItem("Corte").cantidad(1)
                .subtotal(new BigDecimal("30.00")).build();
        Sale sale = Sale.builder().total(new BigDecimal("30.00"))
                .tipAmount(BigDecimal.ZERO).items(List.of(item)).build();
        IssueElectronicDocumentRequest request = new IssueElectronicDocumentRequest(
                1L, ElectronicDocumentType.INVOICE, "1", "12345678", "Cliente", "Cusco", null);
        assertThatThrownBy(() -> factory.build(sale, settings(), request, "secret", "F001", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RUC");
    }

    private ElectronicInvoicingSettings settings() {
        ElectronicInvoicingSettings s = new ElectronicInvoicingSettings();
        s.setFiscalRuc("20100100100"); s.setLegalName("Empresa Demo SAC");
        s.setCommercialName("Demo"); s.setFiscalAddress("Cusco"); s.setUbigeo("080101");
        s.setSalesPointCode("GODS"); s.setAnnexCode("0000"); s.setIgvRate(new BigDecimal("18.00"));
        return s;
    }
}
