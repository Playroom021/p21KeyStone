package com.keyStone.Playroom021.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Body for {@code POST /api/work-orders/{id}/part-usage}. One or more items are applied
 * in a single transaction: if any item fails (unknown part, insufficient stock), none
 * of them are applied.
 */
@Data
public class PartUsageRequest {

    @NotEmpty(message = "items must contain at least one entry")
    @Size(max = 50, message = "items must contain at most 50 entries")
    @Valid
    private List<PartUsageItem> items;

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;
}
