package io.veridraw.drawcore.repository;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Репозиторий для управления сущностями {@link Draw}.
 */
@Repository
public interface DrawRepository extends R2dbcRepository<Draw, UUID> {

    Flux<Draw> findByName(String name);

    Mono<Draw> findByIdAndStatus(UUID id, DrawStatus status);

    Mono<Boolean> existsByName(String name);

    Mono<Draw> findByIdAndStatusIn(UUID id, Collection<DrawStatus> statuses);

    @Query("SELECT * FROM draws WHERE id = :id AND status = :status FOR UPDATE")
    Mono<Draw> findByIdAndStatusLocked(UUID id, DrawStatus status);
}
