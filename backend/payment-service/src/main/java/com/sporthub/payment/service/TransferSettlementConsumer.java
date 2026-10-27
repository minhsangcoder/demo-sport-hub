package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.event.RabbitMQConfig;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class TransferSettlementConsumer {
  public static final String QUEUE = "sporthub.payment.transfer-settlement";
  private final TransferSettlementService settlements;
  private final ObjectMapper json;

  public TransferSettlementConsumer(TransferSettlementService settlements, ObjectMapper json) {
    this.settlements = settlements;
    this.json = json;
  }

  @Bean
  public Queue transferSettlementQueue() {
    return QueueBuilder.durable(QUEUE)
        .deadLetterExchange(RabbitMQConfig.DLX_EXCHANGE)
        .deadLetterRoutingKey("dead-letter")
        .build();
  }

  @Bean
  public Binding transferSettlementBinding(Queue transferSettlementQueue) {
    return BindingBuilder.bind(transferSettlementQueue)
        .to(new TopicExchange(RabbitMQConfig.EXCHANGE_PAYMENT))
        .with("transfer.handoff.completed");
  }

  @RabbitListener(queues = QUEUE)
  public void receive(Message message) throws java.io.IOException {
    var event = json.readTree(message.getBody());
    if (com.sporthub.common.money.PeerResaleContract.isPeer(event.path("payload"))
        || com.sporthub.common.money.PeerResaleContract.isPeer(event.path("payload").path("transferTerms"))) return;
    if (event.path("eventVersion").asInt() == 2
        && event.path("producer").asText().equals("transfer-service")
        && event.path("payload").path("moneyClass").asText().equals("REAL")) return;
    settlements.completed(event);
  }
}
