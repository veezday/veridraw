package io.veridraw.drawcore.service;

import io.micrometer.core.instrument.Counter;
import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.OutboxEvent;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.dto.CreateDrawRequest;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.OutboxRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.management.InstanceAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Сервис управления жизненным циклом розыгрышей.
 * Обеспечивает создание, активацию, завершение и отмену розыгрышей.
 * Интегрирован с внешним генератором случайных чисел и паттерном Transactional Outbox
 * для гарантированной асинхронной доставки событий о результатах.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrawService {

    private final DrawRepository drawRepository;
    private final TicketRepository ticketRepository;
    private final OutboxRepository outboxRepository;
    private final RandomNumberService randomNumberService;

    private final Counter drawCreateTransitionsCounter;
    private final Counter drawActiveTransitionsCounter;
    private final Counter drawCompleteTransitionsCounter;
    private final Counter drawCancelTransitionsCounter;

    /**
     * Создает новый розыгрыш.
     *
     * @param request данные для создания розыгрыша
     * @return сохраненный объект {@link Draw} со статусом {@link DrawStatus#CREATED}
     * @throws InstanceAlreadyExistsException если розыгрыш с таким именем уже существует
     */
    public Mono<Draw> createDraw(CreateDrawRequest request) {
        return drawRepository
                .existsByName(request.name())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new InstanceAlreadyExistsException(
                                "Draw with name '" + request.name() + "' already exists"));
                    }
                    Draw draw = Draw.builder()
                            .name(request.name())
                            .description(request.description())
                            .maxTickets(request.maxTickets())
                            .ticketPrice(request.ticketPrice())
                            .status(DrawStatus.CREATED)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    log.info("Creating new draw: {}", draw.getName());
                    return drawRepository.save(draw);
                })
                .doOnSuccess(ignore -> drawCreateTransitionsCounter.increment());
    }

    /**
     * Возвращает поток всех существующих розыгрышей.
     *
     * @return {@link Flux} со списком всех {@link Draw}
     */
    public Flux<Draw> getAllDraws() {
        return drawRepository.findAll();
    }

    /**
     * Находит розыгрыш по его уникальному идентификатору.
     *
     * @param id идентификатор розыгрыша
     * @return {@link Mono} с найденным {@link Draw} или пустой, если не найден
     */
    public Mono<Draw> getDrawById(UUID id) {
        return drawRepository.findById(id);
    }

    /**
     * Активирует розыгрыш, переводя его из статуса {@link DrawStatus#CREATED} в {@link DrawStatus#ACTIVATED}.
     *
     * @param id идентификатор розыгрыша
     * @return обновленный объект {@link Draw}
     * @throws IllegalStateException если розыгрыш не найден или находится в недопустимом статусе
     */
    public Mono<Draw> activateDraw(UUID id) {
        return drawRepository
                .findByIdAndStatus(id, DrawStatus.CREATED)
                .switchIfEmpty(Mono.error(new IllegalStateException("Draw not found or not in CREATED status")))
                .flatMap(draw -> {
                    draw.setStatus(DrawStatus.ACTIVATED);
                    draw.setUpdatedAt(Instant.now());
                    return drawRepository.save(draw);
                })
                .doOnSuccess(ignore -> drawActiveTransitionsCounter.increment());
    }

    /**
     * Завершает активный розыгрыш: получает список билетов, запрашивает случайный индекс победителя
     * и инициирует финальное сохранение состояния. Внешний вызов к генератору случайных чисел
     * выполняется до начала транзакции БД, чтобы не удерживать соединение.
     *
     * @param drawId идентификатор завершаемого розыгрыша
     * @return обновленный объект {@link Draw} со статусом {@link DrawStatus#COMPLETED}
     * @throws IllegalStateException если розыгрыш не найден, не активен или содержит менее 2 билетов
     */
    public Mono<Draw> completeDraw(UUID drawId) {
        log.info("Completing draw: {}", drawId);

        return drawRepository
                .findByIdAndStatus(drawId, DrawStatus.ACTIVATED)
                .switchIfEmpty(Mono.error(new IllegalStateException("Draw not found or not in ACTIVATED status")))
                .flatMap(draw -> ticketRepository
                        .findByDrawId(drawId)
                        .collectList()
                        .flatMap(tickets -> {
                            if (tickets.size() < 2) {
                                return Mono.error(new IllegalStateException(
                                        "Cannot complete draw: at least 2 tickets are required"));
                            }
                            return randomNumberService
                                    .getRandomInteger(0, tickets.size() - 1)
                                    .flatMap(winnerIndex -> {
                                        Ticket winner = tickets.get(winnerIndex);
                                        return finalizeDrawCompletion(draw, winner);
                                    });
                        }));
    }

    /**
     * Атомарно завершает розыгрыш в рамках одной транзакции:
     * обновляет статус розыгрыша, фиксирует победителя и сохраняет событие в Outbox.
     *
     * @param draw объект розыгрыша
     * @param winner билет победителя
     * @return сохраненный объект {@link Draw}
     */
    @Transactional
    public Mono<Draw> finalizeDrawCompletion(Draw draw, Ticket winner) {
        draw.setWinnerId(winner.getId());
        draw.setStatus(DrawStatus.COMPLETED);
        draw.setCompletedAt(Instant.now());
        draw.setUpdatedAt(Instant.now());

        return drawRepository
                .save(draw)
                .flatMap(savedDraw -> saveOutboxEvent(savedDraw, winner).thenReturn(savedDraw))
                .doOnSuccess(ignore -> drawCompleteTransitionsCounter.increment());
    }

    /**
     * Отменяет розыгрыш, если он находится в статусе {@link DrawStatus#CREATED} или {@link DrawStatus#ACTIVATED}.
     *
     * @param drawId идентификатор отменяемого розыгрыша
     * @return обновленный объект {@link Draw} со статусом {@link DrawStatus#CANCELLED}
     * @throws IllegalStateException если розыгрыш не найден или его статус не позволяет отмену
     */
    public Mono<Draw> cancelDraw(UUID drawId) {
        log.info("Canceling draw: {}", drawId);
        List<DrawStatus> allowedStatuses = List.of(DrawStatus.CREATED, DrawStatus.ACTIVATED);

        return drawRepository
                .findByIdAndStatusIn(drawId, allowedStatuses)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("Draw not found or cannot be cancelled in its current status")))
                .flatMap(draw -> {
                    draw.setStatus(DrawStatus.CANCELLED);
                    draw.setUpdatedAt(Instant.now());
                    return drawRepository.save(draw);
                })
                .doOnSuccess(ignore -> drawCancelTransitionsCounter.increment());
    }

    /**
     * Формирует и сохраняет событие {@link OutboxEvent} о завершении розыгрыша
     * для последующей асинхронной публикации внешним процессом.
     *
     * @param draw завершенный розыгрыш
     * @param winner билет победителя
     * @return сохраненное событие {@link OutboxEvent}
     */
    private Mono<OutboxEvent> saveOutboxEvent(Draw draw, Ticket winner) {
        DrawCompletedEvent event = DrawCompletedEvent.builder()
                .drawId(draw.getId())
                .winnerEmail(winner.getParticipantEmail())
                .winnerName(winner.getParticipantName())
                .drawName(draw.getName())
                .build();

        OutboxEvent outbox = OutboxEvent.builder()
                .aggregateType("Draw")
                .aggregateId(draw.getId())
                .eventType("DrawCompleted")
                .payload(event)
                .createdAt(Instant.now())
                .published(false)
                .build();

        return outboxRepository
                .save(outbox)
                .doOnSuccess(e -> log.debug("Outbox event saved for draw: {}", draw.getId()));
    }
}
