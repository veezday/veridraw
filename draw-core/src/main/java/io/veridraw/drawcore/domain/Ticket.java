package io.veridraw.drawcore.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Доменная сущность билета участника розыгрыша.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tickets")
public class Ticket {
    @Id
    private UUID id;

    /** Идентификатор розыгрыша, к которому относится билет. */
    private UUID drawId;
    /** Имя участника. */
    private String participantName;
    /** Email участника. */
    private String participantEmail;

    /** Момент покупки билета. */
    private Instant purchasedAt;
}
