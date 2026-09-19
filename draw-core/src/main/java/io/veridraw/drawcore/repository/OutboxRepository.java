package io.veridraw.drawcore.repository;

import io.veridraw.drawcore.event.OutboxEvent;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface OutboxRepository extends R2dbcRepository<OutboxEvent, UUID> {

    Flux<OutboxEvent> findByPublishedFalse();
}
