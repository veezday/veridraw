package io.veridraw.drawcore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.veridraw.drawcore.dto.CreateDrawRequest;
import io.veridraw.drawcore.dto.DrawResponse;
import io.veridraw.drawcore.service.DrawService;
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
@RequestMapping("/api/draws")
@RequiredArgsConstructor
@Tag(name = "Draws", description = "API для управления розыгрышами")
public class DrawController {

    private final DrawService drawService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Создать новый розыгрыш")
    @ApiResponse(responseCode = "201", description = "Розыгрыш успешно создан")
    public Mono<ResponseEntity<DrawResponse>> createDraw(@Valid @RequestBody CreateDrawRequest drawDTO) {
        return drawService
                .createDraw(drawDTO)
                .map(DrawResponse::from)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получить все розыгрыши")
    public Flux<DrawResponse> getAllDraws() {
        return drawService.getAllDraws().map(DrawResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получить розыгрыш по ID")
    @ApiResponse(responseCode = "404", description = "Розыгрыш не найден")
    public Mono<ResponseEntity<DrawResponse>> getDrawById(@PathVariable UUID id) {
        return drawService
                .getDrawById(id)
                .map(DrawResponse::from)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Активировать розыгрыш")
    @ApiResponse(responseCode = "400", description = "Ошибка активации")
    public Mono<ResponseEntity<DrawResponse>> activateDraw(@PathVariable UUID id) {
        return drawService
                .activateDraw(id)
                .map(DrawResponse::from)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("ERROR: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Завершить розыгрыш")
    @ApiResponse(responseCode = "400", description = "Ошибка завершения")
    public Mono<ResponseEntity<DrawResponse>> completeDraw(@PathVariable UUID id) {
        return drawService
                .completeDraw(id)
                .map(DrawResponse::from)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("ERROR: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Отменить розыгрыш")
    @ApiResponse(responseCode = "400", description = "Ошибка отмены")
    public Mono<ResponseEntity<DrawResponse>> cancelDraw(@PathVariable UUID id) {
        return drawService
                .cancelDraw(id)
                .map(DrawResponse::from)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("ERROR: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }
}
