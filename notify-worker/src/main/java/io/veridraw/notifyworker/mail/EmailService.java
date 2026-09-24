package io.veridraw.notifyworker.mail;

import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.veridraw.notifyworker.event.InboxEventRepository;
import io.veridraw.shared.event.DrawCompletedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MeterRegistry meterRegistry;
    private final KafkaTemplate<UUID, DrawCompletedEvent> kafkaTemplate;

    private final InboxEventRepository inboxEventRepository;

    private final Counter emailSentSuccessCounter;
    private final Counter emailSentFailedCounter;

    public EmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            MeterRegistry meterRegistry,
            KafkaTemplate<UUID, DrawCompletedEvent> kafkaTemplate,
            InboxEventRepository inboxEventRepository) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.meterRegistry = meterRegistry;
        this.kafkaTemplate = kafkaTemplate;
        this.inboxEventRepository = inboxEventRepository;

        this.emailSentSuccessCounter = Counter.builder("email.sent.total")
                .tag("status", "success")
                .description("Total number of successfully sent emails")
                .register(meterRegistry);

        this.emailSentFailedCounter = Counter.builder("email.sent.total")
                .tag("status", "failed")
                .description("Total number of failed email sending attempts")
                .register(meterRegistry);
    }

    @Retry(name = "emailSendRetry")
    public void sendEmail(DrawCompletedEvent event) {

        log.info("Attempting to send email to: {} for event: {}", event.getWinnerEmail(), event.getDrawId());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(event.getWinnerEmail());
            helper.setSubject("Уведомление от VeriDraw");
            helper.setFrom("noreply@veridraw.local");

            Context context = new Context();
            context.setVariable("name", event.getWinnerName());
            context.setVariable("message", "Поздравляю, вы победили!");
            String htmlContext = templateEngine.process("email-notification", context);

            helper.setText(htmlContext, true);

            mailSender.send(mimeMessage);

            emailSentSuccessCounter.increment();
            log.info("Successfully sent email to: {}", event.getWinnerEmail());

        } catch (MessagingException | MailException e) {
            emailSentFailedCounter.increment();
            log.error("Failed to send email to: {}. Error: {}", event.getWinnerEmail(), e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
