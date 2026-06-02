package com.eventledger.gateway.repository;

import com.eventledger.gateway.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    // Returns events sorted by eventTimestamp (chronological order) for out-of-order tolerance
    List<Event> findByAccountIdOrderByEventTimestampAsc(String accountId);
}