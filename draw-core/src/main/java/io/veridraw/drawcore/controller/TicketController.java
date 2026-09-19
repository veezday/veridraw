package io.veridraw.drawcore.controller;

import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.service.TicketService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public Mono<ResponseEntity<Ticket>> purchaseTicket(@RequestBody Ticket ticket) {
        return ticketService
                .purchaseTicket(ticket)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .doOnError(e -> log.error("ОШИБКА: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/draw/{drawId}")
    public Flux<Ticket> getTicketsByDrawId(@PathVariable UUID drawId) {
        return ticketService.getTicketsByDrawId(drawId);
    }
}
