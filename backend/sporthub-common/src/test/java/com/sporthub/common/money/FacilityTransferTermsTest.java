package com.sporthub.common.money;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.common.security.Signatures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class FacilityTransferTermsTest {
  private final ObjectMapper json = new ObjectMapper();

  private ObjectNode terms(String price, int fee) {
    UUID facility = UUID.randomUUID(), owner = UUID.randomUUID(), seller = UUID.randomUUID();
    var booking =
        json.createObjectNode()
            .put("id", UUID.randomUUID().toString())
            .put("facilityId", facility.toString())
            .put("courtId", UUID.randomUUID().toString());
    booking
        .putObject("refundPolicy")
        .put("configured", true)
        .put("id", UUID.randomUUID().toString())
        .put("version", 1)
        .put("rateBps", 8000)
        .put("currency", "VND")
        .put("rounding", "HALF_UP")
        .put(
            "termsHash",
            Signatures.sha256(facility + "|1|8000|VND|HALF_UP|FACILITY_POLICY_CANCELLATION"));
    var venue =
        json.createObjectNode()
            .put("id", facility.toString())
            .put("ownerId", owner.toString())
            .put("name", "Synthetic facility");
    var policy =
        json.createObjectNode()
            .put("configured", true)
            .put("version", 1)
            .put("feeBps", fee)
            .put(
                "termsHash",
                Signatures.sha256(facility + "|1|" + fee + "|VND|HALF_UP|FACILITY_TRANSFER_FEE"));
    var receiver =
        json.createObjectNode()
            .put("facilityId", facility.toString())
            .put("receiverOwnerId", owner.toString())
            .put("channelId", UUID.randomUUID().toString())
            .put("receiverRevisionId", UUID.randomUUID().toString())
            .put("status", "READY");
    var destination =
        json.createObjectNode()
            .put("id", UUID.randomUUID().toString())
            .put("principalId", seller.toString())
            .put("destinationHash", "a".repeat(64));
    return FacilityTransferTerms.seller(
        json,
        booking,
        venue,
        policy,
        receiver,
        destination,
        seller,
        new BigDecimal(price),
        Instant.parse("2027-01-01T00:00:00Z"));
  }

  @Test
  void ownerExplicitFeesAndHalfUpNeverUseLegacyTwoPercentDefault() {
    for (int bps : new int[] {0, 200, 300, 700, 10000}) {
      var p = terms("100000", bps);
      TransferFinancialTerms.validateSeller(p);
      assertThat(new BigDecimal(p.path("quotedFeeAmount").asText()))
          .isEqualByComparingTo(Integer.toString(bps * 10));
      assertThat(new BigDecimal(p.path("quotedSellerNetAmount").asText()))
          .isEqualByComparingTo(Integer.toString(100000 - bps * 10));
      var acquisition =
          TransferFinancialTerms.acquisition(
              p,
              UUID.randomUUID(),
              1,
              UUID.randomUUID(),
              UUID.randomUUID(),
              Instant.parse("2026-12-31T23:00:00Z"),
              Instant.parse("2026-12-31T22:00:00Z"));
      TransferFinancialTerms.validateAcquisition(acquisition);
      assertThat(TransferFinancialTerms.publicListing(p).toString())
          .doesNotContain(
              "sellerDestination",
              "channelId",
              "receiverRevisionId",
              "accountNumber",
              "apiKey",
              "checksumKey");
      acquisition.put("feeBps", 200);
      if (bps != 200)
        assertThatThrownBy(() -> TransferFinancialTerms.validateAcquisition(acquisition))
            .isInstanceOf(IllegalArgumentException.class);
    }
    var tie = terms("25", 5000);
    assertThat(tie.path("quotedFeeAmount").asText()).isEqualTo("13");
    assertThat(tie.path("quotedSellerNetAmount").asText()).isEqualTo("12");
    var leaked = terms("100000", 700);
    leaked.put("accountNumber", "000123456789");
    assertThatThrownBy(() -> FacilityTransferTerms.validateSeller(leaked))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
