package com.keyStone.Playroom021.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class TimeLogRequest {

    @NotNull(message = "startedAt is required")
    private Instant startedAt;

    @NotNull(message = "endedAt is required")
    private Instant endedAt;

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;
}
