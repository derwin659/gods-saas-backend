package com.gods.saas.invoicing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ElectronicDocumentStatusTest {
    @Test
    void mapsAllDocumentedMifactStatuses() {
        assertThat(ElectronicDocumentStatus.fromMifact("101")).isEqualTo(ElectronicDocumentStatus.PROCESSING);
        assertThat(ElectronicDocumentStatus.fromMifact("102")).isEqualTo(ElectronicDocumentStatus.ACCEPTED);
        assertThat(ElectronicDocumentStatus.fromMifact("103")).isEqualTo(ElectronicDocumentStatus.ACCEPTED_WITH_OBSERVATIONS);
        assertThat(ElectronicDocumentStatus.fromMifact("104")).isEqualTo(ElectronicDocumentStatus.REJECTED);
        assertThat(ElectronicDocumentStatus.fromMifact("105")).isEqualTo(ElectronicDocumentStatus.VOIDED);
        assertThat(ElectronicDocumentStatus.fromMifact("108")).isEqualTo(ElectronicDocumentStatus.VOID_PENDING);
        assertThat(ElectronicDocumentStatus.fromMifact("999")).isEqualTo(ElectronicDocumentStatus.ERROR);
        assertThat(ElectronicDocumentStatus.fromMifact(null)).isEqualTo(ElectronicDocumentStatus.ERROR);
    }
}
