package io.veridraw.notifyworker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import io.veridraw.notifyworker.domain.InboxEvent;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.notifyworker.service.EmailService;
import io.veridraw.notifyworker.sheduler.InboxEventPoller;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class InboxEventPollerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InboxEventRepository inboxEventRepository;

    @Autowired
    private InboxEventPoller inboxEventPoller;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        inboxEventRepository.deleteAll();
    }

    @Test
    void processPendingEvents_WhenValid_ShouldFetchFromDbAndSendEmailExactlyOnce() {
        UUID drawId = UUID.randomUUID();
        DrawCompletedEvent payload = DrawCompletedEvent.builder()
                .drawId(drawId)
                .winnerEmail("winner-test@mailpit.local")
                .winnerName("Ivan Winner")
                .drawName("Super Draw")
                .build();

        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(drawId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(payload)
                .published(false)
                .createdAt(Instant.now())
                .build());

        inboxEventPoller.processPendingEvents();

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            InboxEvent updatedEvent = inboxEventRepository.findByAggregateId(drawId);
            assertThat(updatedEvent.getPublished()).isTrue();
            assertThat(updatedEvent.getPublishedAt()).isNotNull();
            assertThat(getMailpitMessageCount())
                    .as("There should be exactly 1 email")
                    .isEqualTo(1);
        });

        inboxEventPoller.processPendingEvents();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(getMailpitMessageCount())
                    .as("Email should be sent only once")
                    .isEqualTo(1);
        });
    }

    @Test
    void processPendingEvents_WhenEmailFails_ShouldNotMarkAsPublishedAndRetryLater() {
        UUID drawId = UUID.randomUUID();
        DrawCompletedEvent payload = DrawCompletedEvent.builder()
                .drawId(drawId)
                .winnerEmail("fail-test@mailpit.local")
                .winnerName("Error Test")
                .drawName("Failed Draw")
                .build();

        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(drawId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(payload)
                .published(false)
                .createdAt(Instant.now())
                .build());

        doThrow(new MailSendException("Simulated SMTP error"))
                .when(emailService)
                .sendEmail(any(DrawCompletedEvent.class));

        inboxEventPoller.processPendingEvents();

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            InboxEvent updatedEvent = inboxEventRepository.findByAggregateId(drawId);
            assertThat(updatedEvent.getPublished()).isFalse();
            assertThat(updatedEvent.getPublishedAt()).isNull();
            assertThat(getMailpitMessageCount()).isEqualTo(0);
        });
    }

    @Test
    void processPendingEvents_WhenOneEventFails_ShouldProcessOthersSuccessfully() {
        UUID successId = UUID.randomUUID();
        UUID failId = UUID.randomUUID();

        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(successId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(DrawCompletedEvent.builder()
                        .drawId(successId)
                        .winnerEmail("success@mailpit.local")
                        .winnerName("Successful")
                        .drawName("Successful Draw")
                        .build())
                .published(false)
                .createdAt(Instant.now())
                .build());

        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(failId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(DrawCompletedEvent.builder()
                        .drawId(failId)
                        .winnerEmail("fail@mailpit.local")
                        .winnerName("Unsuccessful")
                        .drawName("Failed Draw")
                        .build())
                .published(false)
                .createdAt(Instant.now())
                .build());

        doThrow(new MailSendException("Simulated SMTP error"))
                .when(emailService)
                .sendEmail(argThat(event -> event.getDrawId().equals(failId)));

        inboxEventPoller.processPendingEvents();

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(inboxEventRepository.findByAggregateId(successId).getPublished())
                    .isTrue();
            assertThat(inboxEventRepository.findByAggregateId(failId).getPublished())
                    .isFalse();
            assertThat(getMailpitMessageCount()).isEqualTo(1);
        });
    }

    @Test
    void processPendingEvents_WhenNoEvents_ShouldDoNothing() {
        inboxEventPoller.processPendingEvents();
        assertThat(getMailpitMessageCount()).isEqualTo(0);
    }

    @Test
    void processPendingEvents_WhenRuntimeExceptionThrown_ShouldRollbackTransactionAndKeepEventUnpublished() {
        UUID drawId = UUID.randomUUID();
        DrawCompletedEvent payload = DrawCompletedEvent.builder()
                .drawId(drawId)
                .winnerEmail("rollback-test@mailpit.local")
                .winnerName("Rollback Test")
                .drawName("Rollback Draw")
                .build();

        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(drawId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(payload)
                .published(false)
                .createdAt(Instant.now())
                .build());

        doThrow(new RuntimeException("Simulated critical failure"))
                .when(emailService)
                .sendEmail(any(DrawCompletedEvent.class));

        try {
            inboxEventPoller.processPendingEvents();
        } catch (RuntimeException e) {
        }

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            InboxEvent updatedEvent = inboxEventRepository.findByAggregateId(drawId);
            assertThat(updatedEvent.getPublished())
                    .as("Event should remain unpublished due to transaction rollback")
                    .isFalse();
            assertThat(updatedEvent.getPublishedAt()).isNull();
            assertThat(getMailpitMessageCount()).isEqualTo(0);
        });
    }

    @Test
    void processPendingEvents_WhenCalledConcurrently_ShouldProcessEventOnlyOnce() throws Exception {
        UUID drawId = UUID.randomUUID();
        DrawCompletedEvent payload = DrawCompletedEvent.builder()
                .drawId(drawId)
                .winnerEmail("concurrent-test@mailpit.local")
                .winnerName("Concurrent Test")
                .drawName("Concurrent Draw")
                .build();

        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(drawId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(payload)
                .published(false)
                .createdAt(Instant.now())
                .build());

        CountDownLatch thread1ReachedSend = new CountDownLatch(1);
        CountDownLatch allowThread1ToFinish = new CountDownLatch(1);

        doAnswer(invocation -> {
                    thread1ReachedSend.countDown();
                    allowThread1ToFinish.await(2, TimeUnit.SECONDS);
                    return null;
                })
                .when(emailService)
                .sendEmail(any(DrawCompletedEvent.class));

        Runnable task = () -> {
            try {
                inboxEventPoller.processPendingEvents();
            } catch (Exception e) {
            }
        };

        Thread thread1 = new Thread(task, "Poller-Thread-1");
        thread1.start();

        assertThat(thread1ReachedSend.await(3, TimeUnit.SECONDS))
                .as("Thread 1 should reach sendEmail and hold the DB lock")
                .isTrue();

        Thread thread2 = new Thread(task, "Poller-Thread-2");
        thread2.start();

        Thread.sleep(500);

        allowThread1ToFinish.countDown();

        thread1.join(5000);
        thread2.join(5000);

        assertThat(thread1.isAlive()).as("Thread 1 should have finished").isFalse();
        assertThat(thread2.isAlive()).as("Thread 2 should have finished").isFalse();

        Mockito.verify(emailService, Mockito.times(1)).sendEmail(any(DrawCompletedEvent.class));

        InboxEvent event = inboxEventRepository.findByAggregateId(drawId);
        assertThat(event).isNotNull();
        assertThat(event.getPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
    }
}
