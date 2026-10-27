package com.sporthub.booking.config;

import com.sporthub.common.event.RabbitMQConfig;
import java.time.Clock;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class BookingConfig {
  public static final String PAYMENT_QUEUE = "sporthub.booking.payment-outcomes";
  public static final String BANK_FUNDS_QUEUE = "sporthub.booking.real-bank-funds";
  public static final String ADMISSION_FAILURE_QUEUE = "sporthub.booking.admission-failures";

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public Queue paymentOutcomesQueue() {
    return QueueBuilder.durable(PAYMENT_QUEUE)
        .deadLetterExchange(RabbitMQConfig.DLX_EXCHANGE)
        .deadLetterRoutingKey("dead-letter")
        .build();
  }

  @Bean
  public Binding paymentOutcomesBinding(Queue paymentOutcomesQueue) {
    return BindingBuilder.bind(paymentOutcomesQueue)
        .to(new TopicExchange(RabbitMQConfig.EXCHANGE_PAYMENT))
        .with("payment.completed");
  }

  @Bean
  public Queue realBankFundsQueue() {
    return QueueBuilder.durable(BANK_FUNDS_QUEUE)
        .deadLetterExchange(RabbitMQConfig.DLX_EXCHANGE)
        .deadLetterRoutingKey("dead-letter")
        .build();
  }

  @Bean
  public Declarables admissionFailureBindings() {
    var queue =
        QueueBuilder.durable(ADMISSION_FAILURE_QUEUE)
            .deadLetterExchange(RabbitMQConfig.DLX_EXCHANGE)
            .deadLetterRoutingKey("dead-letter")
            .build();
    return new Declarables(
        queue,
        BindingBuilder.bind(queue)
            .to(new TopicExchange(RabbitMQConfig.EXCHANGE_PAYMENT))
            .with("payment.admission.exempted"));
  }

  @Bean
  public Declarables realBankFundsBindings() {
    var exchange = new TopicExchange(RabbitMQConfig.EXCHANGE_PAYMENT);
    return new Declarables(
        BindingBuilder.bind(realBankFundsQueue()).to(exchange).with("payment.bank.credit"),
        BindingBuilder.bind(realBankFundsQueue()).to(exchange).with("payment.funds.unapplied"));
  }
}
