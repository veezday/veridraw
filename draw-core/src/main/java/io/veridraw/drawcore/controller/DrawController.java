package io.veridraw.drawcore.controller;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.service.DrawService;
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
@RequestMapping("/api/draws")
@RequiredArgsConstructor
public class DrawController {

    private final DrawService drawService;

    @PostMapping
    public Mono<ResponseEntity<Draw>> createDraw(@RequestBody Draw draw) {
        return drawService.createDraw(draw).map(saved -> ResponseEntity.status(HttpStatus.CREATED)
                .body(saved));
    }

    @GetMapping
    public Flux<Draw> getAllDraws() {
        return drawService.getAllDraws();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Draw>> getDrawById(@PathVariable UUID id) {
        return drawService
                .getDrawById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/activate")
    public Mono<ResponseEntity<Draw>> activateDraw(@PathVariable UUID id) {
        return drawService
                .activateDraw(id)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("ОШИБКА: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping("/{id}/complete")
    public Mono<ResponseEntity<Draw>> completeDraw(@PathVariable UUID id) {
        return drawService
                .completeDraw(id)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("ОШИБКА: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping("/{id}/cancel")
    public Mono<ResponseEntity<Draw>> cancelDraw(@PathVariable UUID id) {
        return drawService
                .cancelDraw(id)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("ОШИБКА: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }
}
