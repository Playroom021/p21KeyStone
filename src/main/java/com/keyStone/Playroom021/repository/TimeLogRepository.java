package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.TimeLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimeLogRepository extends JpaRepository<TimeLog, Long> {

    @EntityGraph(attributePaths = {"technician"})
    List<TimeLog> findByWorkOrderIdOrderByStartedAtAscIdAsc(Long workOrderId);

    @EntityGraph(attributePaths = {"technician"})
    Optional<TimeLog> findByIdAndWorkOrderId(Long id, Long workOrderId);
}
