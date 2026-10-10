package com.keyStone.Playroom021.dto;

import com.keyStone.Playroom021.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderRequest {

    @NotNull(message = "Site is required")
    private Long siteId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Priority is required")
    private Priority priority;
}
