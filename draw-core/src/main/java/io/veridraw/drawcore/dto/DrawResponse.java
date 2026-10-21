package io.veridraw.drawcore.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ответ с информацией о розыгрыше")
public record DrawResponse(
        @Schema(description = "Идентификатор розыгрыша", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Название розыгрыша", example = "Летняя лотерея 2024")
        String name,

        @Schema(description = "Описание розыгрыша") String description,

        @Schema(description = "Максимальное количество билетов", example = "1000")
        Integer maxTickets,

        @Schema(description = "Стоимость одного билета", example = "99.99")
        BigDecimal ticketPrice,

        @Schema(description = "Статус розыгрыша") DrawStatus status,
        @Schema(description = "Идентификатор победителя") UUID winnerId,
        @Schema(description = "Дата создания") Instant createdAt,
        @Schema(description = "Дата последнего обновления") Instant updatedAt,
        @Schema(description = "Дата завершения") Instant completedAt) {

    public static DrawResponse from(Draw draw) {
        return new DrawResponse(
                draw.getId(),
                draw.getName(),
                draw.getDescription(),
                draw.getMaxTickets(),
                draw.getTicketPrice(),
                draw.getStatus(),
                draw.getWinnerId(),
                draw.getCreatedAt(),
                draw.getUpdatedAt(),
                draw.getCompletedAt());
    }
}
