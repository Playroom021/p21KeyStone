package com.keyStone.Playroom021.dto;

import com.keyStone.Playroom021.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    /**
     * Required only when role == LOCAL_CUSTOMER — becomes the Customer
     * (company/account) this user's portal is scoped to. Validated
     * conditionally in AuthService rather than with @NotBlank here, since
     * it's optional for every other role.
     */
    private String companyName;
}
