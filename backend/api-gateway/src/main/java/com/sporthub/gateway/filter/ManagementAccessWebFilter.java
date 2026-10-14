package com.sporthub.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Covers Actuator handlers, which do not run through Gateway GlobalFilters. */
@Component
public class ManagementAccessWebFilter implements WebFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        HttpMethod method = exchange.getRequest().getMethod();
        boolean health = (path.equals("/actuator/health") || path.startsWith("/actuator/health/"))
                && (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method));
        if ((path.equals("/actuator") || path.startsWith("/actuator/")) && !health) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
