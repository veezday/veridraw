package io.veridraw.notifyworker.domain;

import io.veridraw.shared.event.DomainEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Доменная сущность входящего события.
 * Хранит данные сообщения до момента его успешной обработки бизнес-логикой.
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "inbox_events")
public class InboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    private String aggregateType;
    private UUID aggregateId;
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    private DomainEvent payload;

    private Instant createdAt;
    private Boolean published;
    private Instant publishedAt;
}
