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
public class PartResponse {
    private Long id;
    private String sku;
    private String name;
    private String description;
    private String unit;
    private int quantityOnHand;
    private int reorderLevel;
    private boolean lowStock;
    private BigDecimal unitCost;
    private Instant createdAt;
    private Instant updatedAt;
}
