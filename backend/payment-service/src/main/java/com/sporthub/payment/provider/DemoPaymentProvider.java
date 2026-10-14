package com.sporthub.payment.provider;

import static com.sporthub.payment.web.PaymentDtos.*;
import com.sporthub.common.security.Signatures;
import com.sporthub.common.exception.ForbiddenException;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Local adapter. Simulated provider sends the same signed HTTP callback as a real provider would. */
@Component @Profile("(local | demo) & !prod & !production")
public class DemoPaymentProvider implements PaymentProvider {
 private final String secret;private final RestClient self;private final java.time.Clock clock;
 public DemoPaymentProvider(@Value("${sporthub.payment.demo.callback-secret}") String secret,@Value("${PAYMENT_SERVICE_URL:http://localhost:8085}") String url,java.time.Clock clock){
  if(secret.length()<32)throw new IllegalArgumentException("Demo callback secret must contain at least 32 characters");this.secret=secret;this.clock=clock;
  var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build());factory.setReadTimeout(java.time.Duration.ofSeconds(5));self=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 public String name(){return "DEMO";}
 public String create(UUID payment,BigDecimal amount,String currency){return "DEMO-"+payment;}
 public String sign(Callback c){return Signatures.hmac(secret,c.paymentId()+"|"+c.providerReference()+"|"+c.transactionId()+"|"+c.amount().setScale(2).toPlainString()+"|"+c.currency()+"|"+c.status()+"|"+c.timestamp());}
 public void verify(Callback c,String signature){long now=clock.instant().getEpochSecond();if(c.timestamp()<now-300||c.timestamp()>now+300||!Signatures.matches(sign(c),signature))throw new ForbiddenException("Callback signature or timestamp is invalid");}
 public void simulate(Payment payment,String outcome){var callback=new Callback(payment.id(),payment.providerReference(),"TX-"+payment.id(),payment.amount(),payment.currency(),outcome,clock.instant().getEpochSecond());
  try{self.post().uri("/api/v1/payments/providers/demo/callback").header("X-Provider-Signature",sign(callback)).body(callback).retrieve().toBodilessEntity();}
  catch(org.springframework.web.client.HttpClientErrorException ex){throw new com.sporthub.common.exception.ConflictException("DEMO callback was rejected; reload the verified payment state");}
  catch(org.springframework.web.client.RestClientException ex){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"DEMO callback is unavailable; retry the same payment");}
 }
 public String refund(String transaction,BigDecimal amount,String idempotencyKey){return "REFUND-"+Signatures.sha256(transaction+"|"+idempotencyKey);}
}
