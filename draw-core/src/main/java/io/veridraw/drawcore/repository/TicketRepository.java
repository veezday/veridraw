package io.veridraw.drawcore.repository;

import io.veridraw.drawcore.domain.Ticket;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TicketRepository extends R2dbcRepository<Ticket, UUID> {

    Flux<Ticket> findByDrawId(UUID drawId);

    Mono<Ticket> findByDrawIdAndParticipantEmail(UUID drawId, String email);

    Mono<Long> countByDrawId(UUID drawId);
}
