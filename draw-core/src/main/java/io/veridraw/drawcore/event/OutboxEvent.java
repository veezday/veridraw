package io.veridraw.drawcore.event;

import java.time.Instant;
import java.util.UUID;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table("outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;

    private String aggregateType;
    private UUID aggregateId;
    private String eventType;
    private Json payload;
    private Instant createdAt;
    private Boolean published;
    private Instant publishedAt;
}
