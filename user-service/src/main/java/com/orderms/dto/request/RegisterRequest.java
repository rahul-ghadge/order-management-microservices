package com.orderms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

// ── Register ──────────────────────────────────────────────────────────────────
@Data
public class RegisterRequest {
    @NotBlank @Size(min = 3, max = 50)
    private String username;
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9]).*$",
             message = "Password must contain at least one uppercase letter and one digit")
    private String password;
    @Size(max = 100) private String firstName;
    @Size(max = 100) private String lastName;
    @Pattern(regexp = "^[+]?[0-9]{7,15}$", message = "Invalid phone number")
    private String phone;
}
