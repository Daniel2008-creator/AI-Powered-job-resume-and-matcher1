package com.talentlens.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateInput(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(min = 20) String text) {
}