package io.veridraw.drawcore.domain;

import io.veridraw.shared.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Запись в outbox-таблице для паттерна Transactional Outbox.
 * Хранит доменное событие до момента его успешной публикации.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;

    /** Тип агрегата-источника события. */
    private String aggregateType;
    /** Идентификатор агрегата-источника. */
    private UUID aggregateId;
    /** Тип доменного события. */
    private String eventType;
    /** Тело события. */
    private DomainEvent payload;
    /** Момент создания записи. */
    private Instant createdAt;
    /** Флаг успешной публикации. */
    private Boolean published;
    /** Момент публикации. */
    private Instant publishedAt;
}
