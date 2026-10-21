package io.veridraw.notifyworker.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.veridraw.shared.event.DrawCompletedEvent;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private Counter emailSentSuccessCounter;

    @Mock
    private Counter emailSentFailedCounter;

    @Mock
    private Counter emailSendAttemptsCounter;

    @Mock
    private Counter emailRetryExhaustedCounter;

    @Mock
    private Timer emailSendDurationTimer;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    private final DrawCompletedEvent testEvent =
            new DrawCompletedEvent(UUID.randomUUID(), "winner@example.com", "Ivan Ivanov", "Super Draw");

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(templateEngine.process(eq("email-notification"), any(Context.class)))
                .thenReturn("<html>Mocked HTML</html>");
    }

    @Test
    void sendEmail_success_sendsEmail() {
        emailService.sendEmail(testEvent);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_mailException_throwsRuntimeException() {
        doThrow(new MailSendException("SMTP Server is down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendEmail(testEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to send email")
                .hasCauseInstanceOf(MailSendException.class);
    }
}
