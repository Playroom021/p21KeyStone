package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.WorkOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderStatusHistoryRepository extends JpaRepository<WorkOrderStatusHistory, Long> {

    List<WorkOrderStatusHistory> findByWorkOrderIdOrderByChangedAtAsc(Long workOrderId);

    // Used only when deleting a work order that never left NEW (its sole history row).
    void deleteByWorkOrderId(Long workOrderId);
}
