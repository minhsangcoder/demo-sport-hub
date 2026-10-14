package com.sporthub.payment.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
@Configuration @EnableScheduling
public class PaymentTimeConfig {
    @Bean public Clock paymentClock(){return Clock.systemUTC();}
}
