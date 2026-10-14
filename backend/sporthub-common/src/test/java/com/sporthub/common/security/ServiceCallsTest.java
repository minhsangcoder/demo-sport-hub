package com.sporthub.common.security;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.sporthub.common.exception.ForbiddenException;
import java.time.Instant;
class ServiceCallsTest {
 @Test void privateSignatureBindsMethodPathTimeAndExactBody(){String secret="test-private-key-longer-than-thirty-two-characters",path="/api/v1/internal/bookings/transfer/complete",body="{\"bookingId\":\"123\"}";long now=Instant.now().getEpochSecond();String signature=ServiceCalls.sign(secret,"POST",path,now,body);assertThatCode(()->ServiceCalls.verify(secret,"POST",path,""+now,body,signature)).doesNotThrowAnyException();assertThatThrownBy(()->ServiceCalls.verify(secret,"POST",path,""+now,body+" ",signature)).isInstanceOf(ForbiddenException.class);assertThatThrownBy(()->ServiceCalls.verify(secret,"GET",path,""+now,body,signature)).isInstanceOf(ForbiddenException.class);assertThatThrownBy(()->ServiceCalls.verify(secret,"POST",path,""+(now-301),body,ServiceCalls.sign(secret,"POST",path,now-301,body))).isInstanceOf(ForbiddenException.class);}

 @Test void rejectsFutureExtremeAndWrongPathSignatures(){
  String secret="test-private-key-longer-than-thirty-two-characters",path="/api/v1/internal/bookings/transfer/complete",body="{}";
  for(long time:new long[]{Long.MIN_VALUE,Long.MAX_VALUE,java.time.Instant.now().getEpochSecond()+400}) {
   assertThatThrownBy(()->ServiceCalls.verify(secret,"POST",path,Long.toString(time),body,ServiceCalls.sign(secret,"POST",path,time,body))).isInstanceOf(ForbiddenException.class);
  }
  long now=java.time.Instant.now().getEpochSecond();
  assertThatThrownBy(()->ServiceCalls.verify(secret,"POST","/other",Long.toString(now),body,ServiceCalls.sign(secret,"POST",path,now,body))).isInstanceOf(ForbiddenException.class);
 }

}
