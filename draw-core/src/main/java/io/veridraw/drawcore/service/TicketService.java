package io.veridraw.drawcore.service;

import io.micrometer.core.instrument.Counter;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.dto.PurchaseTicketRequest;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Сервис для управления билетами участников розыгрыша.
 * Обеспечивает покупку билетов с проверкой статуса розыгрыша и защитой от дубликатов на уровне БД.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final DrawRepository drawRepository;

    private final Counter ticketPurchasesCounter;
    private final Counter ticketPurchaseDuplicateErrorsCounter;
    private final Counter ticketPurchaseDrawNotActiveErrorsCounter;
    private final Counter ticketPurchaseSoldOutErrorsCounter;

    /**
     * Оформляет покупку билета для указанного розыгрыша.
     *
     * @param request данные для покупки билета
     * @return сохраненный билет
     * @throws IllegalStateException если розыгрыш не найден, не активен, или билет с таким email уже существует
     */
    @Transactional
    public Mono<Ticket> purchaseTicket(PurchaseTicketRequest request) {
        log.info("Purchasing ticket for draw: {}", request.drawId());

        return drawRepository
                .findByIdAndStatusLocked(request.drawId(), DrawStatus.ACTIVATED)
                .switchIfEmpty(Mono.defer(() -> {
                    ticketPurchaseDrawNotActiveErrorsCounter.increment();
                    return Mono.error(new IllegalStateException("Draw not found or not active"));
                }))
                .flatMap(draw -> ticketRepository.countByDrawId(draw.getId()).flatMap(count -> {
                    if (count >= draw.getMaxTickets()) {
                        ticketPurchaseSoldOutErrorsCounter.increment();
                        return Mono.error(new IllegalStateException("All tickets for this draw have been sold"));
                    }
                    return createAndSaveTicket(request);
                }));
    }

    /**
     * Возвращает все билеты, принадлежащие указанному розыгрышу.
     *
     * @param drawId идентификатор розыгрыша
     * @return поток билетов
     */
    public Flux<Ticket> getTicketsByDrawId(UUID drawId) {
        return ticketRepository.findByDrawId(drawId);
    }

    /**
     * Создает объект билета и сохраняет его в базе данных.
     * Обрабатывает ошибки нарушения целостности данных (дубликаты).
     */
    private Mono<Ticket> createAndSaveTicket(PurchaseTicketRequest request) {
        Ticket ticket = Ticket.builder()
                .drawId(request.drawId())
                .participantName(request.participantName())
                .participantEmail(request.participantEmail())
                .purchasedAt(Instant.now())
                .build();

        return ticketRepository
                .save(ticket)
                .doOnSuccess(ignore -> ticketPurchasesCounter.increment())
                .onErrorResume(DataIntegrityViolationException.class, ex -> handleDuplicateTicket(request, ex));
    }

    /**
     * Обрабатывает попытку покупки дублирующегося билета.
     * Инкрементирует счетчик ошибок, логирует предупреждение и возвращает понятную ошибку.
     */
    private Mono<Ticket> handleDuplicateTicket(PurchaseTicketRequest request, DataIntegrityViolationException ex) {
        ticketPurchaseDuplicateErrorsCounter.increment();
        log.warn(
                "Attempt to purchase duplicate ticket. DrawId: {}, Email: {}",
                request.drawId(),
                request.participantEmail());

        return Mono.error(new IllegalStateException("A ticket for this email already exists in this draw"));
    }
}
