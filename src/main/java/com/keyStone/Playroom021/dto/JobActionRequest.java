package com.keyStone.Playroom021.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Optional body for the technician job actions (start/hold/resume/complete). */
@Data
public class JobActionRequest {

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;
}
