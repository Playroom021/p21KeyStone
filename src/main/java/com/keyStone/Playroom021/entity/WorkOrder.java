package com.keyStone.Playroom021.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The central object of the domain: a digital representation of a maintenance
 * job. Everything else — customer, site, technician, status history, and (in
 * future) parts/time entries — hangs off this record.
 */
@Entity
@Table(name = "work_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-facing identifier, e.g. "WO-1001". Assigned right after insert, once the id is known. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderStatus status;

    /** Null until a manager/dispatcher assigns someone — not settable by the customer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    private User assignedTechnician;

    private Instant slaDueAt;

    /** When the work order first reached COMPLETED; null until then. Used to judge SLA for finished work. */
    private Instant completedAt;

    /**
     * SLA state as last recorded by SlaService (on create/priority change/status change and by the
     * scheduled check). API responses use a live evaluation instead; this column lets the scheduler
     * detect changes. Null for CANCELLED work or work without an SLA.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SlaStatus slaStatus;

    /** When SlaService first recorded AT_RISK for the current SLA window. */
    private Instant slaAtRiskAt;

    /** When SlaService first recorded BREACHED (detection time, not the due time). */
    private Instant slaBreachedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
