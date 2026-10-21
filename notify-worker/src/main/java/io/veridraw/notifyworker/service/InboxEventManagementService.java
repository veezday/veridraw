package io.veridraw.notifyworker.service;

import io.veridraw.notifyworker.dto.InboxEventResponse;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.notifyworker.sheduler.InboxEventPoller;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Сервис для административного управления и мониторинга входящих событий.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxEventManagementService {

    private final InboxEventRepository inboxEventRepository;
    private final InboxEventPoller inboxEventPoller;

    /**
     * Возвращает список всех событий, ожидающих обработки.
     *
     * @return список DTO событий
     */
    public List<InboxEventResponse> getUnpublishedEvents() {
        log.info("Fetching all unpublished inbox events");
        return inboxEventRepository.findByPublishedFalse().stream()
                .map(InboxEventResponse::from)
                .toList();
    }

    public void resendUnpublishedEvents() {
        log.info("Resending all unpublished inbox events");
        inboxEventPoller.processPendingEvents();
    }
}
