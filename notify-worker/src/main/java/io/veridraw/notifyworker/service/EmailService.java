package io.veridraw.notifyworker.service;

import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.veridraw.shared.event.DrawCompletedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Сервис отправки email-уведомлений.
 * Использует шаблоны Thymeleaf и механизмы автоматических повторных попыток (Retry).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    private final Counter emailSentSuccessCounter;
    private final Counter emailSentFailedCounter;
    private final Counter emailSendAttemptsCounter;
    private final Counter emailRetryExhaustedCounter;
    private final Timer emailSendDurationTimer;
    private final MeterRegistry meterRegistry;

    /**
     * Отправляет email победителю.
     *
     * @param event данные о завершенном розыгрыше
     * @throws RuntimeException если отправка не удалась после всех попыток
     */
    @Retry(name = "emailSendRetry", fallbackMethod = "sendEmailFallback")
    public void sendEmail(DrawCompletedEvent event) {
        emailSendAttemptsCounter.increment();
        Timer.Sample sample = Timer.start(meterRegistry);
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
        } finally {
            sample.stop(emailSendDurationTimer);
        }
    }

    /**
     * Метод обратного вызова при исчерпании всех попыток отправки.
     */
    private void sendEmailFallback(DrawCompletedEvent event, Exception e) throws RuntimeException {
        emailRetryExhaustedCounter.increment();
        log.error(
                "All retry attempts exhausted for email to: {}. Manual intervention required.",
                event.getWinnerEmail(),
                e);
        throw new RuntimeException("Email retry exhausted", e);
    }
}
