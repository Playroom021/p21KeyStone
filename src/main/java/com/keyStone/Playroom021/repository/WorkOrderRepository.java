package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    // Ownership-scoped lookups: a customer can only ever fetch their own work orders,
    // by construction of the query itself, not by an after-the-fact permission check.
    List<WorkOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<WorkOrder> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, com.keyStone.Playroom021.entity.WorkOrderStatus status);

    Optional<WorkOrder> findByIdAndCustomerId(Long id, Long customerId);

    // Same idea, for a TECHNICIAN: a work order they are not assigned to simply doesn't match.
    Optional<WorkOrder> findByIdAndAssignedTechnicianId(Long id, Long technicianId);

    long countByCustomerId(Long customerId);

    boolean existsByCustomerId(Long customerId);

    boolean existsBySiteId(Long siteId);

    // Step 5: fetch customer/site/assignedTechnician in the same query so the paged
    // Work Order API doesn't issue extra queries per row when rendering names.
    @Override
    @EntityGraph(attributePaths = {"customer", "site", "assignedTechnician"})
    Page<WorkOrder> findAll(Specification<WorkOrder> spec, Pageable pageable);

    // ---- Step 7: SLA monitoring + dashboard aggregation ----

    /**
     * Open work whose recorded SLA state could still change: everything not already recorded as BREACHED
     * (a breached open work order stays breached until it finishes, and finishing re-evaluates it).
     */
    @Query("select w from WorkOrder w where w.status in :statuses and w.slaDueAt is not null "
            + "and (w.slaStatus is null or w.slaStatus <> :breached)")
    List<WorkOrder> findOpenForSlaCheck(@Param("statuses") Collection<com.keyStone.Playroom021.entity.WorkOrderStatus> statuses,
                                        @Param("breached") com.keyStone.Playroom021.entity.SlaStatus breached);

    @Query("select w.status, count(w) from WorkOrder w group by w.status")
    List<Object[]> countGroupedByStatus();

    @Query("select new com.keyStone.Playroom021.repository.WorkOrderSlaRow(w.id, w.code, w.title, w.priority, w.status, "
            + "w.slaDueAt, w.createdAt, t.id, t.fullName) "
            + "from WorkOrder w left join w.assignedTechnician t where w.status in :statuses")
    List<WorkOrderSlaRow> findSlaRowsByStatusIn(@Param("statuses") Collection<com.keyStone.Playroom021.entity.WorkOrderStatus> statuses);

    /** Finished work completed on or before its due time (SLA met). Same rule as SlaService.evaluate. */
    @Query("select count(w) from WorkOrder w where w.status in :statuses and w.completedAt is not null "
            + "and w.slaDueAt is not null and w.completedAt <= w.slaDueAt")
    long countFinishedWithinSla(@Param("statuses") Collection<com.keyStone.Playroom021.entity.WorkOrderStatus> statuses);

    /** Finished work completed after its due time (SLA breached). */
    @Query("select count(w) from WorkOrder w where w.status in :statuses and w.completedAt is not null "
            + "and w.slaDueAt is not null and w.completedAt > w.slaDueAt")
    long countFinishedLate(@Param("statuses") Collection<com.keyStone.Playroom021.entity.WorkOrderStatus> statuses);

    @Query("select w.assignedTechnician.id, count(w) from WorkOrder w "
            + "where w.assignedTechnician is not null and w.status in :statuses group by w.assignedTechnician.id")
    List<Object[]> countByTechnicianAndStatusIn(@Param("statuses") Collection<com.keyStone.Playroom021.entity.WorkOrderStatus> statuses);
}
