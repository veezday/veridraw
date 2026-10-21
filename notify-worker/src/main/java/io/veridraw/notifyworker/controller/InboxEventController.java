package io.veridraw.notifyworker.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.veridraw.notifyworker.dto.InboxEventResponse;
import io.veridraw.notifyworker.service.InboxEventManagementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контроллер для административного управления входящими событиями (Inbox).
 */
@Slf4j
@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
@Tag(name = "Inbox Admin", description = "API для мониторинга и ручного управления очередью входящих событий")
public class InboxEventController {

    private final Counter adminResendTriggeredCounter;
    private final DistributionSummary adminUnpublishedListSizeSummary;

    private final InboxEventManagementService managementService;

    /**
     * Возвращает список всех событий, которые находятся в статусе "не обработано".
     *
     * @return список событий
     */
    @GetMapping("/unpublished")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получить все необработанные события")
    @ApiResponse(responseCode = "200", description = "Список успешно получен")
    public ResponseEntity<List<InboxEventResponse>> getUnpublishedEvents() {
        log.info("request: get unpublished events");
        List<InboxEventResponse> events = managementService.getUnpublishedEvents();

        adminUnpublishedListSizeSummary.record(events.size());
        return ResponseEntity.ok(events);
    }

    @PostMapping("/resend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Отправить не отправленные сообщения")
    @ApiResponse(responseCode = "200", description = "Метод выполнен")
    public void resendUnpublishedEvents() {
        log.info("request: resend unpublished events");
        adminResendTriggeredCounter.increment();
        managementService.resendUnpublishedEvents();
    }
}
