package io.veridraw.drawcore.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "dto для создания нового розыгрыша")
public record CreateDrawRequest(
        @Schema(description = "Название розыгрыша", example = "Летняя лотерея 2024")
        @NotBlank(message = "Draw name must not be blank") @Size(max = 255, message = "Draw name must be at most 255 characters long") String name,

        @Schema(description = "Описание розыгрыша", example = "Розыгрыш призов для активных пользователей")
        @NotBlank(message = "Description must not be blank") @Size(max = 1000, message = "Draw description must be at most 1000 characters long") String description,

        @Schema(description = "Максимальное количество билетов", example = "1000")
        @NotNull(message = "Maximum tickets must not be null") @Positive(message = "Maximum tickets must be greater than zero") Integer maxTickets,

        @Schema(description = "Стоимость одного билета", example = "99.99")
        @NotNull(message = "Ticket price must not be null") @Positive(message = "Ticket price must be greater than zero") BigDecimal ticketPrice) {}
