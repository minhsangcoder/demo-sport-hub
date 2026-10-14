package com.sporthub.common.money;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

class TransferHandoffTest {
 private final UUID listing=UUID.randomUUID(),booking=UUID.randomUUID(),seller=UUID.randomUUID(),acquisition=UUID.randomUUID(),buyer=UUID.randomUUID(),payment=UUID.randomUUID();
 private final Instant time=Instant.parse("2026-10-13T00:00:00Z");
 private String fp(BigDecimal amount,UUID paid){return TransferHandoff.fingerprint(listing,booking,seller,acquisition,buyer,paid,amount,"VND",time,time.plusSeconds(600),time.plusSeconds(3600));}
 @Test void decimalFormattingDoesNotChangeMoneyIdentityButPaymentAmountAndScopeDo(){
  String original=fp(new BigDecimal("100000"),payment);assertThat(fp(new BigDecimal("100000.00"),payment)).isEqualTo(original);
  assertThat(fp(new BigDecimal("100001"),payment)).isNotEqualTo(original);assertThat(fp(new BigDecimal("100000"),UUID.randomUUID())).isNotEqualTo(original);
  assertThat(TransferHandoff.fingerprint(listing,booking,seller,acquisition,UUID.randomUUID(),payment,new BigDecimal("100000"),"VND",time,time.plusSeconds(600),time.plusSeconds(3600))).isNotEqualTo(original);
 }
 @Test void incompleteFractionalOrNegativeFinancialCommandsCannotProduceProof(){
  assertThatThrownBy(()->fp(new BigDecimal("0.25"),payment)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->fp(new BigDecimal("-1"),payment)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->fp(new BigDecimal("1"),null)).isInstanceOf(IllegalArgumentException.class);
 }
}
