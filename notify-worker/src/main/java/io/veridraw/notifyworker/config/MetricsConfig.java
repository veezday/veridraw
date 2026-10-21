package io.veridraw.notifyworker.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация метрик
 * */
@Configuration
public class MetricsConfig {
    @Bean
    public Counter kafkaMessagesReceivedCounter(MeterRegistry registry) {
        return Counter.builder("notify_worker.kafka.messages.received")
                .description("Total messages successfully received and saved from Kafka topic")
                .register(registry);
    }

    @Bean
    public Counter kafkaMessagesSkippedIdempotentCounter(MeterRegistry registry) {
        return Counter.builder("notify_worker.kafka.messages.skipped.idempotent")
                .description("Total messages skipped due to idempotency (already exists in DB)")
                .register(registry);
    }

    @Bean
    public Counter kafkaMessagesProcessingErrorsCounter(MeterRegistry registry) {
        return Counter.builder("notify_worker.kafka.messages.errors")
                .description("Total messages that failed to be saved or processed")
                .register(registry);
    }

    @Bean
    public Counter emailSentSuccessCounter(MeterRegistry registry) {
        return Counter.builder("email.sent.total")
                .tag("status", "success")
                .description("Total number of successfully sent emails")
                .register(registry);
    }

    @Bean
    public Counter emailSentFailedCounter(MeterRegistry registry) {
        return Counter.builder("email.sent.total")
                .tag("status", "failed")
                .description("Total number of failed email sending attempts")
                .register(registry);
    }

    @Bean
    public Timer inboxProcessingDurationTimer(MeterRegistry registry) {
        return Timer.builder("notify_worker.inbox.processing.duration")
                .description("Total time to process a single event (save + email + update)")
                .register(registry);
    }

    @Bean
    public Counter emailSendAttemptsCounter(MeterRegistry registry) {
        return Counter.builder("notify_worker.email.send.attempts")
                .description("Total email send attempts (including retries)")
                .register(registry);
    }

    @Bean
    public Timer emailSendDurationTimer(MeterRegistry registry) {
        return Timer.builder("notify_worker.email.send.duration")
                .description("Time for a single email send attempt (render + SMTP)")
                .register(registry);
    }

    @Bean
    public Counter emailRetryExhaustedCounter(MeterRegistry registry) {
        return Counter.builder("notify_worker.email.retry.exhausted")
                .description("Total cases when all 3 Resilience4j retry attempts failed")
                .register(registry);
    }

    @Bean
    public Counter adminResendTriggeredCounter(MeterRegistry registry) {
        return Counter.builder("notify_worker.admin.resend.triggered.total")
                .description("Number of times the manual resend endpoint was called by an admin")
                .register(registry);
    }

    @Bean
    public DistributionSummary adminUnpublishedListSizeSummary(MeterRegistry registry) {
        return DistributionSummary.builder("notify_worker.admin.unpublished_list.size")
                .description(
                        "Size of the unpublished events list returned via API (monitors potential memory/payload issues)")
                .register(registry);
    }

    @Bean
    public Timer pollerBatchDurationTimer(MeterRegistry registry) {
        return Timer.builder("notify_worker.poller.batch.duration.seconds")
                .description("Time taken to execute one full poller cycle (fetch + process all pending events)")
                .register(registry);
    }

    @Bean
    public DistributionSummary pollerBatchSizeSummary(MeterRegistry registry) {
        return DistributionSummary.builder("notify_worker.poller.batch.size")
                .description("Number of pending events fetched from DB in a single poller cycle")
                .register(registry);
    }

    @Bean
    public Counter pollerEventSkippedAlreadyPublishedCounter(MeterRegistry registry) {
        return Counter.builder("notify_worker.poller.event.skipped_already_published.total")
                .description(
                        "Number of events skipped during polling because they were already marked as published (concurrency protection)")
                .register(registry);
    }
}
