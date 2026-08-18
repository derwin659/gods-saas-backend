package com.gods.saas.invoicing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gods.saas.domain.dto.request.IssueElectronicDocumentRequest;
import com.gods.saas.domain.model.ElectronicInvoicingSettings;
import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.model.SaleItem;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class MifactSalePayloadFactory {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final ObjectMapper mapper;
    public MifactSalePayloadFactory(ObjectMapper mapper) { this.mapper = mapper; }

    public ObjectNode build(Sale sale, ElectronicInvoicingSettings settings,
                            IssueElectronicDocumentRequest request, String token,
                            String series, long sequence) {
        validateReceiver(request);
        BigDecimal rate = settings.getIgvRate();
        BigDecimal divisor = BigDecimal.ONE.add(rate.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal fiscalTotal = money(nz(sale.getTotal()).subtract(nz(sale.getTipAmount())));
        if (fiscalTotal.signum() <= 0) throw new IllegalArgumentException("La venta fiscal debe ser mayor a cero");
        List<SaleItem> items = sale.getItems();
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("La venta no tiene items");
        BigDecimal sourceTotal = items.stream().map(i -> nz(i.getSubtotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sourceTotal.signum() <= 0) throw new IllegalArgumentException("Los items no tienen importe fiscal");

        ArrayNode lines = mapper.createArrayNode();
        BigDecimal assignedGross = BigDecimal.ZERO;
        BigDecimal assignedNet = BigDecimal.ZERO;
        BigDecimal assignedTax = BigDecimal.ZERO;
        for (int index = 0; index < items.size(); index++) {
            SaleItem item = items.get(index);
            BigDecimal gross = index == items.size() - 1
                    ? fiscalTotal.subtract(assignedGross)
                    : money(nz(item.getSubtotal()).multiply(fiscalTotal).divide(sourceTotal, 8, RoundingMode.HALF_UP));
            BigDecimal net = money(gross.divide(divisor, 8, RoundingMode.HALF_UP));
            BigDecimal tax = gross.subtract(net).setScale(2, RoundingMode.HALF_UP);
            int quantity = item.getCantidad() == null || item.getCantidad() < 1 ? 1 : item.getCantidad();
            ObjectNode line = lines.addObject();
            line.put("COD_ITEM", item.getId() == null ? "ITEM-" + (index + 1) : "ITEM-" + item.getId());
            line.put("COD_UNID_ITEM", "NIU");
            line.put("CANT_UNID_ITEM", Integer.toString(quantity));
            line.put("VAL_UNIT_ITEM", amount(net.divide(BigDecimal.valueOf(quantity), 8, RoundingMode.HALF_UP)));
            line.put("PRC_VTA_UNIT_ITEM", amount(gross.divide(BigDecimal.valueOf(quantity), 8, RoundingMode.HALF_UP)));
            line.put("VAL_VTA_ITEM", amount(net));
            line.put("MNT_PV_ITEM", amount(gross));
            line.put("COD_TIP_PRC_VTA", "01");
            line.put("COD_TIP_AFECT_IGV_ITEM", "10");
            line.put("COD_TRIB_IGV_ITEM", "1000");
            line.put("POR_IGV_ITEM", rate.stripTrailingZeros().toPlainString());
            line.put("MNT_IGV_ITEM", amount(tax));
            line.put("TXT_DESC_ITEM", clean(item.getNombreItem(), "Item"));
            assignedGross = assignedGross.add(gross);
            assignedNet = assignedNet.add(net);
            assignedTax = assignedTax.add(tax);
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("TOKEN", token);
        root.put("COD_TIP_NIF_EMIS", "6");
        root.put("NUM_NIF_EMIS", settings.getFiscalRuc());
        root.put("NOM_RZN_SOC_EMIS", settings.getLegalName());
        root.put("NOM_COMER_EMIS", clean(settings.getCommercialName(), settings.getLegalName()));
        root.put("COD_UBI_EMIS", settings.getUbigeo());
        root.put("TXT_DMCL_FISC_EMIS", settings.getFiscalAddress());
        root.put("COD_TIP_NIF_RECP", request.receiverDocumentType());
        root.put("NUM_NIF_RECP", request.receiverDocumentNumber());
        root.put("NOM_RZN_SOC_RECP", request.receiverName());
        root.put("TXT_DMCL_FISC_RECEP", clean(request.receiverAddress(), "-") );
        LocalDate date = sale.getSaleDate() != null ? sale.getSaleDate().toLocalDate()
                : sale.getFechaCreacion() != null ? sale.getFechaCreacion().toLocalDate() : LocalDate.now();
        root.put("FEC_EMIS", date.toString());
        root.put("FEC_VENCIMIENTO", date.toString());
        root.put("COD_TIP_CPE", request.documentType().getSunatCode());
        root.put("NUM_SERIE_CPE", series);
        root.put("NUM_CORRE_CPE", String.format("%08d", sequence));
        root.put("COD_MND", "PEN");
        if (request.receiverEmail() != null && !request.receiverEmail().isBlank()) root.put("TXT_CORREO_ENVIO", request.receiverEmail().trim());
        root.put("COD_PRCD_CARGA", "001");
        root.put("MNT_TOT_GRAVADO", amount(assignedNet));
        root.put("MNT_TOT_TRIB_IGV", amount(assignedTax));
        root.put("MNT_TOT", amount(fiscalTotal));
        root.put("COD_PTO_VENTA", settings.getSalesPointCode());
        root.put("ENVIAR_A_SUNAT", "true");
        root.put("RETORNA_XML_ENVIO", "true");
        root.put("RETORNA_XML_CDR", "true");
        root.put("RETORNA_PDF", "true");
        root.put("COD_FORM_IMPR", "004");
        root.put("TXT_VERS_UBL", "2.1");
        root.put("TXT_VERS_ESTRUCT_UBL", "2.0");
        root.put("COD_ANEXO_EMIS", settings.getAnnexCode());
        root.put("COD_TIP_OPE_SUNAT", "0101");
        root.set("items", lines);
        return root;
    }

    private void validateReceiver(IssueElectronicDocumentRequest r) {
        if (r == null || r.documentType() == null || r.branchId() == null) throw new IllegalArgumentException("Tipo y sede son obligatorios");
        if (r.documentType() != ElectronicDocumentType.INVOICE && r.documentType() != ElectronicDocumentType.RECEIPT)
            throw new IllegalArgumentException("El piloto solo permite factura o boleta");
        if (r.receiverDocumentNumber() == null || r.receiverDocumentNumber().isBlank() || r.receiverName() == null || r.receiverName().isBlank())
            throw new IllegalArgumentException("Documento y nombre fiscal del cliente son obligatorios");
        if (r.documentType() == ElectronicDocumentType.INVOICE && (!"6".equals(r.receiverDocumentType()) || !r.receiverDocumentNumber().matches("\\d{11}")))
            throw new IllegalArgumentException("La factura requiere tipo 6 y RUC de 11 digitos");
    }
    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String amount(BigDecimal value) { return money(value).toPlainString(); }
    private String clean(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
}
