package com.sporthub.common.money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DemoRefundMathTest {
 @Test void roundsTheTargetOnceAndBreaksTiesByPaymentId(){
  var payer=UUID.randomUUID();var ids=List.of(new UUID(0,1),new UUID(0,2),new UUID(0,3));
  var payments=ids.stream().map(id->new DemoRefundMath.Payment(id,payer,BigDecimal.ONE)).toList();
  var a=DemoRefundMath.allocate(payments,6667);
  assertThat(a.stream().map(DemoRefundMath.Allocation::refundAmount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("2");
  assertThat(a.get(0).refundAmount()).isEqualByComparingTo("1");assertThat(a.get(1).refundAmount()).isEqualByComparingTo("1");assertThat(a.get(2).refundAmount()).isZero();
  var reversed=new ArrayList<>(payments);Collections.reverse(reversed);assertThat(DemoRefundMath.allocate(reversed,6667)).isEqualTo(a);
 }
 @Test void partialCollectionsZeroAndFullPolicies(){
  var p=new DemoRefundMath.Payment(UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("100000"));
  assertThat(DemoRefundMath.allocate(List.of(p),5000).getFirst().refundAmount()).isEqualByComparingTo("50000");
  assertThat(DemoRefundMath.allocate(List.of(p),0).getFirst().refundAmount()).isZero();
  assertThat(DemoRefundMath.allocate(List.of(p),10000).getFirst().refundAmount()).isEqualByComparingTo("100000");
  assertThat(DemoRefundMath.target(new BigDecimal("25"),5000)).isEqualByComparingTo("13");
  assertThatThrownBy(()->DemoRefundMath.allocate(List.of(p,p),5000)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->DemoRefundMath.target(BigDecimal.ONE,10001)).isInstanceOf(IllegalArgumentException.class);
 }
}
