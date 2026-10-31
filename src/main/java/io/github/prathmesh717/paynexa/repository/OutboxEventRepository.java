package io.github.prathmesh717.paynexa.repository;

import io.github.prathmesh717.paynexa.domain.OutboxEvent;
import io.github.prathmesh717.paynexa.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent,Long> {
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' AND e.nextRetryAt <= :now")
    List<OutboxEvent> findPendingToProcess(@Param("now") LocalDateTime now, Pageable pageable);

    long countByStatus(OutboxStatus status);

}
