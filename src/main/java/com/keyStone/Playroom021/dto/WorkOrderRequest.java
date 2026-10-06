package com.keyStone.Playroom021.dto;

import com.keyStone.Playroom021.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderRequest {

    @NotNull(message = "Site is required")
    private Long siteId;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotNull(message = "Priority is required")
    private Priority priority;
}
