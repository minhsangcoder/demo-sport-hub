package com.sporthub.common.money;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JsonNode money read across service boundaries must not pass through DoubleNode. */
@Configuration
public class ExactDecimalJson {
    public static org.springframework.web.client.RestClient.Builder restClient(org.springframework.web.client.RestClient.Builder builder){
        return builder.messageConverters(converters->{for(var converter:converters)if(converter instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter jackson)jackson.getObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);});
    }
    @Bean public Jackson2ObjectMapperBuilderCustomizer exactDecimalAmounts() {
        return builder->builder.featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
