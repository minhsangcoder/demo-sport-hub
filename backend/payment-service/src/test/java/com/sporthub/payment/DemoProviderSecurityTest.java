package com.sporthub.payment;
import static org.assertj.core.api.Assertions.*;
import com.sporthub.payment.provider.DemoPaymentProvider;
import com.sporthub.payment.web.PaymentDtos.Callback;
import com.sporthub.common.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
class DemoProviderSecurityTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-13T09:00:00Z"),ZoneOffset.UTC);
    private final String secret="synthetic-provider-signing-key-over-thirty-two-characters";
    @Test void timestampBoundsRejectExpiredFutureAndExtremeSignedCallbacks(){
        var provider=new DemoPaymentProvider(secret,"http://127.0.0.1:9",clock);
        for(long time:new long[]{clock.instant().getEpochSecond()-301,clock.instant().getEpochSecond()+301,Long.MIN_VALUE,Long.MAX_VALUE}){
            var c=new Callback(UUID.randomUUID(),"DEMO-reference","TX-reference",new BigDecimal("100000.25"),"VND","SUCCESS",time);
            assertThatThrownBy(()->provider.verify(c,provider.sign(c))).isInstanceOf(ForbiddenException.class);
        }
        var good=new Callback(UUID.randomUUID(),"DEMO-reference","TX-reference",new BigDecimal("100000.25"),"VND","SUCCESS",clock.instant().getEpochSecond());
        provider.verify(good,provider.sign(good));assertThatThrownBy(()->provider.verify(good,null)).isInstanceOf(ForbiddenException.class);
    }
    @Test void productionProfilesExcludeDemoEvenWhenMixedWithLocalOrDemo(){
        for(String[] profiles:new String[][]{{"local"},{"demo"},{"prod"},{"production"},{"prod","demo"},{"production","local"}}){
            try(var context=new AnnotationConfigApplicationContext()){
                context.getEnvironment().setActiveProfiles(profiles);
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("sporthub.payment.demo.callback-secret",secret,"PAYMENT_SERVICE_URL","http://127.0.0.1:9")));
                context.registerBean(Clock.class,()->clock);context.register(DemoPaymentProvider.class);context.refresh();
                boolean enabled=profiles.length==1&&(profiles[0].equals("local")||profiles[0].equals("demo"));
                assertThat(context.getBeansOfType(DemoPaymentProvider.class)).hasSize(enabled?1:0);
            }
        }
    }
}
