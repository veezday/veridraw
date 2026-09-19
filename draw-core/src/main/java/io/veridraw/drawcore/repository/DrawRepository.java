package io.veridraw.drawcore.repository;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DrawRepository extends R2dbcRepository<Draw, UUID> {

    Flux<Draw> findByStatus(DrawStatus status);

    Mono<Draw> findByIdAndStatus(UUID id, DrawStatus status);
}
