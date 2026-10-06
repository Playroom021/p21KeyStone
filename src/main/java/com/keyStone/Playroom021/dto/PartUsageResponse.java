package com.keyStone.Playroom021.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartUsageResponse {
    private Long id;
    private Long workOrderId;
    private Long partId;
    private String partSku;
    private String partName;
    private int quantity;
    private BigDecimal unitCostAtUse;
    private BigDecimal lineCost;
    /** Stock left for this part right after the usage was recorded; null when listing existing usages. */
    private Integer remainingStock;
    private String note;
    private String usedByEmail;
    private String usedByRole;
    private Instant usedAt;
}
