package io.veridraw.drawcore.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "dto для запроса на покупку билета")
public record PurchaseTicketRequest(
        @Schema(description = "Идентификатор розыгрыша", example = "123e4567-e89b-12d3-a456-426614174000")
        @NotNull(message = "Draw ID must not be null") UUID drawId,

        @Schema(description = "Имя участника", example = "Иван Иванов")
        @NotBlank(message = "Participant name must not be blank") @Size(max = 255, message = "Participant name must be at most 255 characters long") String participantName,

        @Schema(description = "Email участника", example = "ivan@example.com")
        @NotBlank(message = "Participant email must not be blank") @Email(message = "Participant email must be a valid email address") @Size(max = 255, message = "Participant email must be at most 255 characters long") String participantEmail) {}
