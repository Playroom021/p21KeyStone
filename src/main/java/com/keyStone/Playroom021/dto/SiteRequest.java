package com.keyStone.Playroom021.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteRequest {

    @NotBlank(message = "Site name is required")
    @Size(max = 150, message = "Site name must be at most 150 characters")
    private String name;

    @Size(max = 255, message = "Address must be at most 255 characters")
    private String addressLine;

    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;
}
