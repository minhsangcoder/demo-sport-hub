package com.sporthub.common.money;
import com.fasterxml.jackson.databind.*;
import com.sporthub.common.security.Signatures;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DemoTransferTermsTest {
 ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 @Test void facilityPolicyWorksWithoutBankOrBeneficiaryAndSurvivesAcquisition(){
  UUID facility=UUID.randomUUID(),owner=UUID.randomUUID(),seller=UUID.randomUUID();
  var booking=json.createObjectNode().put("id",UUID.randomUUID().toString()).put("facilityId",facility.toString()).put("courtId",UUID.randomUUID().toString());
  booking.set("refundPolicy",json.valueToTree(Map.of("configured",true,"id",UUID.randomUUID(),"version",1,"rateBps",8000,"currency","VND","rounding","HALF_UP","termsHash",Signatures.sha256(facility+"|1|8000|VND|HALF_UP|FACILITY_POLICY_CANCELLATION"))));
  var receiver=json.createObjectNode().put("id",facility.toString()).put("ownerId",owner.toString()).put("name","Demo Facility");
  var policy=json.createObjectNode().put("configured",true).put("version",3).put("feeBps",500).put("termsHash",Signatures.sha256(facility+"|3|500|VND|HALF_UP|FACILITY_TRANSFER_FEE"));
  var now=Instant.parse("2026-10-22T00:00:00Z");
  var terms=DemoTransferTerms.seller(json,booking,receiver,policy,seller,new BigDecimal("100000"),now.plusSeconds(7200));
  assertThat(terms.path("quotedFeeAmount").asText()).isEqualTo("5000");assertThat(terms.path("quotedSellerNetAmount").asText()).isEqualTo("95000");
  assertThat(terms.has("beneficiaryRevisionId")).isFalse();assertThat(terms.has("providerAccount")).isFalse();
  var acquisition=TransferFinancialTerms.acquisition(terms,UUID.randomUUID(),2,UUID.randomUUID(),UUID.randomUUID(),now.plusSeconds(600),now);
  assertThatCode(()->TransferFinancialTerms.validateAcquisition(acquisition)).doesNotThrowAnyException();
  var publicView=TransferFinancialTerms.publicListing(terms).toString();assertThat(publicView).contains("Demo Facility","DEMO").doesNotContain(seller.toString(),owner.toString(),"bookingId");
  var changed=acquisition.deepCopy();changed.put("receiverOwnerId",UUID.randomUUID().toString());
  assertThatThrownBy(()->TransferFinancialTerms.validateAcquisition(changed)).isInstanceOf(IllegalArgumentException.class);
  policy.put("configured",false);assertThatThrownBy(()->DemoTransferTerms.seller(json,booking,receiver,policy,seller,BigDecimal.ONE,now.plusSeconds(7200))).isInstanceOf(IllegalArgumentException.class);
 }
}
