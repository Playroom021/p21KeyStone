package com.keyStone.Playroom021.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerRequest {

    @NotBlank(message = "Company name is required")
    @Size(max = 150, message = "Company name must be at most 150 characters")
    private String companyName;

    @Email(message = "Contact email must be a valid email address")
    @Size(max = 150, message = "Contact email must be at most 150 characters")
    private String contactEmail;
}
