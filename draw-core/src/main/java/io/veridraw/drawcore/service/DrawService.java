package io.veridraw.drawcore.service;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.event.OutboxEvent;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.OutboxRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import io.veridraw.shared.event.DomainEvent;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrawService {

    private final DrawRepository drawRepository;
    private final TicketRepository ticketRepository;
    private final OutboxRepository outboxRepository;

    private final RandomNumberService randomNumberService;

    private final ObjectMapper objectMapper;

    public Mono<Draw> createDraw(Draw draw) {
        log.info("Creating new draw: {}", draw.getName());
        draw.setStatus(DrawStatus.CREATED);
        draw.setCreatedAt(Instant.now());
        draw.setUpdatedAt(Instant.now());
        return drawRepository.save(draw);
    }

    public Flux<Draw> getAllDraws() {
        return drawRepository.findAll();
    }

    public Mono<Draw> getDrawById(UUID id) {
        return drawRepository.findById(id);
    }

    public Mono<Draw> activateDraw(UUID id) {
        return drawRepository
                .findByIdAndStatus(id, DrawStatus.CREATED)
                .switchIfEmpty(Mono.error(new IllegalStateException("Draw not found or not in CREATED status")))
                .flatMap(draw -> {
                    draw.setStatus(DrawStatus.ACTIVE);
                    draw.setUpdatedAt(Instant.now());
                    return drawRepository.save(draw);
                });
    }

    public Mono<Draw> completeDraw(UUID drawId) {
        log.info("Completing draw: {}", drawId);

        return drawRepository
                .findByIdAndStatus(drawId, DrawStatus.ACTIVE)
                .switchIfEmpty(Mono.error(new IllegalStateException("Draw not found or not in ACTIVE status")))
                .flatMap(draw ->
                        // Получаем все билеты
                        ticketRepository.findByDrawId(drawId).collectList().flatMap(tickets -> {
                            if (tickets.isEmpty()) {
                                return Mono.error(new IllegalStateException("No tickets purchased for this draw"));
                            }

                            // Выбираем случайного победителя
                            return randomNumberService
                                    .getRandomInteger(0, tickets.size() - 1)
                                    .flatMap(winnerIndex -> {
                                        Ticket winnerTicket = tickets.get(winnerIndex);

                                        draw.setWinnerId(winnerTicket.getId());
                                        draw.setStatus(DrawStatus.COMPLETED);
                                        draw.setCompletedAt(Instant.now());
                                        draw.setUpdatedAt(Instant.now());

                                        return drawRepository.save(draw).flatMap(savedDraw -> {
                                            DrawCompletedEvent event = DrawCompletedEvent.builder()
                                                    .drawId(savedDraw.getId())
                                                    .winnerEmail(winnerTicket.getParticipantEmail())
                                                    .winnerName(winnerTicket.getParticipantName())
                                                    .drawName(savedDraw.getName())
                                                    .build();

                                            OutboxEvent outbox = OutboxEvent.builder()
                                                    .aggregateType("Draw")
                                                    .aggregateId(savedDraw.getId())
                                                    .eventType("DrawCompleted")
                                                    .payload(event)
                                                    .createdAt(Instant.now())
                                                    .published(false)
                                                    .build();

                                            return outboxRepository
                                                    .save(outbox)
                                                    .doOnSuccess(e -> log.debug(
                                                            "Outbox event saved for draw: {}", savedDraw.getId()))
                                                    .thenReturn(savedDraw);
                                        });
                                    });
                        }));
    }

    public Mono<Draw> cancelDraw(UUID drawId) {
        log.info("Canceling draw: {}", drawId);
        return drawRepository
                .findByIdAndStatus(drawId, DrawStatus.ACTIVE)
                .switchIfEmpty(Mono.error(new IllegalStateException("Draw not found or not in ACTIVE status")))
                .flatMap(draw -> {
                    draw.setStatus(DrawStatus.CANCELLED);
                    draw.setUpdatedAt(Instant.now());
                    return drawRepository.save(draw).then(Mono.just(draw));
                });
    }

    private String toJsonString(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
    }
}
