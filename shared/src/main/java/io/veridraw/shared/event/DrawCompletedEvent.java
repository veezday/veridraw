package io.veridraw.shared.event;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие, публикуемое при успешном завершении розыгрыша.
 * Содержит информацию о победителе для отправки уведомления.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawCompletedEvent implements DomainEvent {

    /** Идентификатор завершенного розыгрыша. */
    UUID drawId;

    /** Email победителя для отправки уведомления. */
    String winnerEmail;

    /** Имя победителя. */
    String winnerName;

    /** Название розыгрыша. */
    String drawName;
}
