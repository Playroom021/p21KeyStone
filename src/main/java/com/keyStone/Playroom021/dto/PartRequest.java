package com.keyStone.Playroom021.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 50, message = "SKU must be at most 50 characters")
    private String sku;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    @NotBlank(message = "Unit is required")
    @Size(max = 20, message = "Unit must be at most 20 characters")
    private String unit;

    @NotNull(message = "quantityOnHand is required")
    @Min(value = 0, message = "quantityOnHand must be 0 or greater")
    @Max(value = 100000000, message = "quantityOnHand is too large")
    private Integer quantityOnHand;

    @NotNull(message = "reorderLevel is required")
    @Min(value = 0, message = "reorderLevel must be 0 or greater")
    @Max(value = 100000000, message = "reorderLevel is too large")
    private Integer reorderLevel;

    @NotNull(message = "unitCost is required")
    @DecimalMin(value = "0.00", message = "unitCost must be 0 or greater")
    @DecimalMax(value = "9999999999.99", message = "unitCost is too large")
    private BigDecimal unitCost;
}
