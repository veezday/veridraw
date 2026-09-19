package io.veridraw.drawcore.service;

import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final DrawRepository drawRepository;

    public Mono<Ticket> purchaseTicket(Ticket ticket) {
        log.info("Purchasing ticket for draw: {}", ticket.getDrawId());

        return drawRepository
                .findByIdAndStatus(ticket.getDrawId(), DrawStatus.ACTIVE)
                .switchIfEmpty(Mono.error(new IllegalStateException("Draw not found or not active")))
                .flatMap(draw -> {
                    ticket.setStatus("PURCHASED");
                    ticket.setPurchasedAt(Instant.now());
                    return ticketRepository.save(ticket).onErrorResume(DataIntegrityViolationException.class, ex -> {
                        log.warn(
                                "Attempt to purchase duplicate ticket. DrawId: {}, Email: {}",
                                ticket.getDrawId(),
                                ticket.getParticipantEmail());

                        return Mono.error(
                                new IllegalStateException("A ticket for this email already exists in this draw"));
                    });
                });
    }

    public Flux<Ticket> getTicketsByDrawId(UUID drawId) {
        return ticketRepository.findByDrawId(drawId);
    }
}
