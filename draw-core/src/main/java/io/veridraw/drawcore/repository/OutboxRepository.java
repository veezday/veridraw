package io.veridraw.drawcore.repository;

import io.veridraw.drawcore.domain.OutboxEvent;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Репозиторий для управления событиями {@link OutboxEvent}.
 */
@Repository
public interface OutboxRepository extends R2dbcRepository<OutboxEvent, UUID> {

    Flux<OutboxEvent> findByPublishedFalse();

    Mono<Long> countByPublishedFalse();
}
