package io.veridraw.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация метрик
 * */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter ipRateLimitHitsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("gateway_rate_limit_hits_total")
                .description("Total rate limit hits by IP")
                .tag("limiter", "ip")
                .register(meterRegistry);
    }
}
