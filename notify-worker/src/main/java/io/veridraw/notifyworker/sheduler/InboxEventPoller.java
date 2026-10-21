package io.veridraw.notifyworker.sheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.veridraw.notifyworker.domain.InboxEvent;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.notifyworker.service.EmailService;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Процессор входящих событий.
 * Выполняет бизнес-логику и обновляет статус события в БД в рамках одной транзакции.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxEventPoller {

    private final Timer pollerBatchDurationTimer;
    private final DistributionSummary pollerBatchSizeSummary;
    private final Counter pollerEventSkippedAlreadyPublishedCounter;

    private final InboxEventRepository inboxEventRepository;
    private final EmailService emailService;

    @Transactional
    public void processPendingEvents() {
        pollerBatchDurationTimer.record(() -> {
            List<InboxEvent> pendingEvents = inboxEventRepository.findByPublishedFalseForUpdate();

            pollerBatchSizeSummary.record(pendingEvents.size());

            for (InboxEvent event : pendingEvents) {
                try {
                    processInboxEvent(event);
                } catch (Exception e) {
                    log.error("Failed to process event {}. Will retry later.", event.getAggregateId(), e);
                }
            }
        });
    }

    /**
     * Обрабатывает событие: отправляет email и помечает запись как обработанную.
     *
     * @param inbox событие
     * @throws IllegalStateException если запись не найдена
     * @throws RuntimeException если отправить письмо не удалось
     */
    private void processInboxEvent(InboxEvent inbox) {
        if (inbox.getPublished()) {
            pollerEventSkippedAlreadyPublishedCounter.increment();
            log.info("Event {} already processed. Skipping.", inbox.getAggregateId());
            return;
        }

        DrawCompletedEvent event = (DrawCompletedEvent) inbox.getPayload();
        emailService.sendEmail(event);

        inbox.setPublished(true);
        inbox.setPublishedAt(Instant.now());
        inboxEventRepository.save(inbox);

        log.info("Event {} successfully processed and marked as published.", inbox.getAggregateId());
    }
}
