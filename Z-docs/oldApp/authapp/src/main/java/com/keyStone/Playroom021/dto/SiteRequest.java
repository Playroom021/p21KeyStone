package com.keyStone.Playroom021.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SiteRequest {

    @NotBlank(message = "Site name is required")
    private String name;

    private String addressLine;

    private String city;
}
