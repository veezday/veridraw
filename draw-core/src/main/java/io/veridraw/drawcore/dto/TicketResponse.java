package io.veridraw.drawcore.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.veridraw.drawcore.domain.Ticket;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ответ с информацией о билете")
public record TicketResponse(
        @Schema(description = "Идентификатор билета", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Идентификатор розыгрыша") UUID drawId,

        @Schema(description = "Имя участника", example = "Иван Иванов")
        String participantName,

        @Schema(description = "Email участника", example = "ivan@example.com")
        String participantEmail,

        @Schema(description = "Дата покупки билета") Instant purchasedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getDrawId(),
                ticket.getParticipantName(),
                ticket.getParticipantEmail(),
                ticket.getPurchasedAt());
    }
}
