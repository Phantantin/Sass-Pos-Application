package com.tindev.payload.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(@NotBlank @Size(max = 120) String fullName, @Email @NotBlank String email,
                            @NotBlank @Size(min = 8, max = 100) String password, @Size(max = 30) String phone) {
}
