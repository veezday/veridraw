package io.veridraw.notifyworker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Результат операции пакетного повторного запуска обработки событий")
public record RetryResultResponse(
        @Schema(description = "Общее количество найденных необработанных событий")
        int total,

        @Schema(description = "Количество успешно обработанных событий")
        int success,

        @Schema(description = "Количество событий, при обработке которых произошла ошибка")
        int failed) {}
