package com.talentlens.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank @Size(min = 5, max = 200) String email,
        @NotBlank @Size(min = 6, max = 200) String password) {
}