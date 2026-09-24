package io.veridraw.notifyworker.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {
    boolean existsByAggregateId(UUID id);

    InboxEvent findByAggregateId(UUID id);
}
