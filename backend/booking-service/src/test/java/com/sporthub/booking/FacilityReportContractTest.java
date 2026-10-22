package com.sporthub.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.web.FacilityReportDtos;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class FacilityReportContractTest {
    @Test void serializesMoneyExactlyAndKeepsAvailabilityAndMoneyClassExplicit() throws Exception {
        var totals = new FacilityReportDtos.MoneyTotals("VND", FacilityReportDtos.MoneyClass.DEMO,
                FacilityReportDtos.decimal(new BigDecimal("99999999999999999999.25")), "80000", "-80000",
                null, "80000", "0", "0", FacilityReportDtos.Availability.PARTIAL);
        var json = new ObjectMapper().valueToTree(totals);
        assertThat(json.path("gross").isTextual()).isTrue();
        assertThat(json.path("gross").asText()).isEqualTo("99999999999999999999.25");
        assertThat(json.path("refundDue").isNull()).isTrue();
        assertThat(json.path("moneyClass").asText()).isEqualTo("DEMO");
        assertThat(json.path("availability").asText()).isEqualTo("PARTIAL");
        assertThat(json.path("afterRefund").asText()).isEqualTo("-80000");
    }
}
