package io.veridraw.notifyworker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.veridraw.notifyworker.domain.InboxEvent;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Краткая информация о событии во входящем ящике")
public record InboxEventResponse(
        @Schema(description = "Идентификатор записи в БД") UUID id,
        @Schema(description = "Тип агрегата") String aggregateType,
        @Schema(description = "Идентификатор агрегata") UUID aggregateId,
        @Schema(description = "Тип события") String eventType,
        @Schema(description = "Время создания записи") Instant createdAt,
        @Schema(description = "Статус обработки") Boolean published) {

    public static InboxEventResponse from(InboxEvent event) {
        return new InboxEventResponse(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getCreatedAt(),
                event.getPublished());
    }
}
