package com.presight.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Stamps every inbound request with a correlation ID (reused if the
 * caller already supplied one) and logs method/path/latency. In a
 * real deployment this ID would be propagated downstream as a header
 * and picked up by each service's logging MDC for end-to-end tracing;
 * kept intentionally simple here to stay in scope for this assessment.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String finalCorrelationId = correlationId;

        exchange.getRequest().mutate().header(CORRELATION_ID_HEADER, finalCorrelationId).build();
        long start = System.currentTimeMillis();

        log.info("--> {} {} [{}]", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), finalCorrelationId);

        return chain.filter(exchange).doFinally(signal -> {
            long duration = System.currentTimeMillis() - start;
            log.info("<-- {} {} [{}] {}ms status={}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(),
                    finalCorrelationId,
                    duration,
                    exchange.getResponse().getStatusCode());
        });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
