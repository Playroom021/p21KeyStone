package com.keyStone.Playroom021.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** One consumption of a part on a work order. Unit cost is snapshotted at the time of use. */
@Entity
@Table(name = "part_usages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCostAtUse;

    @Column(length = 500)
    private String note;

    @Column(length = 150)
    private String usedByEmail;

    @Column(length = 30)
    private String usedByRole;

    @Column(nullable = false, updatable = false)
    private Instant usedAt;

    @PrePersist
    protected void onCreate() {
        this.usedAt = Instant.now();
    }
}
