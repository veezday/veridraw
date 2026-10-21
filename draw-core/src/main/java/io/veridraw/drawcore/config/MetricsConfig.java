package io.veridraw.drawcore.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.veridraw.drawcore.repository.OutboxRepository;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация метрик
 * */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter drawCreateTransitionsCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.draw.state.transitions")
                .description("Number of successful draw state transitions")
                .tag("target_status", "CREATED")
                .register(registry);
    }

    @Bean
    public Counter drawActiveTransitionsCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.draw.state.transitions")
                .description("Number of successful draw state transitions")
                .tag("target_status", "ACTIVATED")
                .register(registry);
    }

    @Bean
    public Counter drawCompleteTransitionsCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.draw.state.transitions")
                .description("Number of successful draw state transitions")
                .tag("target_status", "COMPLETED")
                .register(registry);
    }

    @Bean
    public Counter drawCancelTransitionsCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.draw.state.transitions")
                .description("Number of successful draw state transitions")
                .tag("target_status", "CANCELLED")
                .register(registry);
    }

    @Bean
    public Counter ticketPurchasesCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.ticket.purchases")
                .description("Total number of successfully purchased tickets")
                .register(registry);
    }

    @Bean
    public Counter ticketPurchaseDuplicateErrorsCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.ticket.purchase.errors")
                .description("Total failed ticket purchase attempts")
                .tag("error_type", "DUPLICATE_TICKET")
                .register(registry);
    }

    @Bean
    public Counter ticketPurchaseDrawNotActiveErrorsCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.ticket.purchase.errors")
                .description("Total failed ticket purchase attempts")
                .tag("error_type", "DRAW_NOT_ACTIVE")
                .register(registry);
    }

    @Bean
    public Counter ticketPurchaseSoldOutErrorsCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.ticket.purchase.errors")
                .description("Total failed ticket purchase attempts")
                .tag("error_type", "DRAW_NOT_ACTIVE")
                .register(registry);
    }

    @Bean
    public Counter outboxPublishedCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.outbox.events.published")
                .description("Total events successfully sent to Kafka and marked as published")
                .register(registry);
    }

    @Bean
    public Counter outboxPublishErrorsCounter(MeterRegistry registry) {
        return Counter.builder("drawcore.outbox.publish.errors")
                .description("Total failures when sending listener to Kafka or updating DB")
                .register(registry);
    }

    @Bean
    public Timer outboxPublishDurationTimer(MeterRegistry registry) {
        return Timer.builder("drawcore.outbox.publish.duration")
                .description("Time taken to read one listener from DB and publish to Kafka")
                .register(registry);
    }

    @Bean
    public Gauge outboxPendingEventsGauge(OutboxRepository outboxRepository, MeterRegistry registry) {
        return Gauge.builder("drawcore.outbox.pending.events", outboxRepository, repo -> {
                    return repo.countByPublishedFalse()
                            .defaultIfEmpty(0L)
                            .block(Duration.ofSeconds(1))
                            .doubleValue();
                })
                .description("Current number of events in DB with published=false")
                .register(registry);
    }
}
