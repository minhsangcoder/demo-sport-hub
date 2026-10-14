package com.sporthub.common.money;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {
    @Test void moneyIsExactWithoutSilentRoundingAndUsesExistingStorageLimit() {
        assertThat(Money.positive(new BigDecimal("0.10")).add(Money.positive(new BigDecimal("0.20")))).isEqualByComparingTo("0.30");
        assertThat(Money.positive(Money.MAX)).isEqualByComparingTo("9999999999.99");
        for(String value:new String[]{"0","-0.01","0.001","10000000000"})assertThatThrownBy(()->Money.positive(new BigDecimal(value))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->Money.positive(new BigDecimal("1"),"USD")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void restClientReadsJsonMoneyAsDecimalNodes()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{byte[] reply="{\"amount\":9999999999.99,\"fraction\":0.10}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,reply.length);exchange.getResponseBody().write(reply);exchange.close();});server.start();
        try {
            var client=ExactDecimalJson.restClient(RestClient.builder()).baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).build();
            var data=client.get().retrieve().body(JsonNode.class);assertThat(data.path("amount").isBigDecimal()).isTrue();assertThat(data.path("amount").decimalValue()).isEqualByComparingTo("9999999999.99");assertThat(data.path("fraction").decimalValue()).isEqualByComparingTo("0.10");
        }finally{server.stop(0);}
    }
}
