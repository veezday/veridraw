package io.veridraw.drawcore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.veridraw.drawcore.dto.PurchaseTicketRequest;
import io.veridraw.drawcore.dto.TicketResponse;
import io.veridraw.drawcore.service.TicketService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "API для управления билетами")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Купить билет")
    @ApiResponse(responseCode = "201", description = "Билет успешно куплен")
    @ApiResponse(responseCode = "400", description = "Ошибка покупки билета")
    public Mono<ResponseEntity<TicketResponse>> purchaseTicket(@Valid @RequestBody PurchaseTicketRequest ticketDTO) {
        return ticketService
                .purchaseTicket(ticketDTO)
                .map(TicketResponse::from)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .doOnError(e -> log.error("ОШИБКА: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/draw/{drawId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получить билеты по ID розыгрыша")
    public Flux<TicketResponse> getTicketsByDrawId(@PathVariable UUID drawId) {
        return ticketService.getTicketsByDrawId(drawId).map(TicketResponse::from);
    }
}
