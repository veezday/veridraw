package io.veridraw.notifyworker.repository;

import io.veridraw.notifyworker.domain.InboxEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для управления записями входящих событий.
 */
@Repository
public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {

    boolean existsByAggregateId(UUID id);

    List<InboxEvent> findByPublishedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM InboxEvent e WHERE e.published = false")
    List<InboxEvent> findByPublishedFalseForUpdate();

    InboxEvent findByAggregateId(UUID id);
}
