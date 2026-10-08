package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // Ownership-scoped lookups: a customer can only ever fetch their own work orders,
    // by construction of the query itself, not by an after-the-fact permission check.
    List<WorkOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<WorkOrder> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, com.keyStone.Playroom021.entity.WorkOrderStatus status);

    Optional<WorkOrder> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerId(Long customerId);
}
