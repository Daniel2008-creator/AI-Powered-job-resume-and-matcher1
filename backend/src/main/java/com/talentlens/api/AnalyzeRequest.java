package com.talentlens.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnalyzeRequest(
        @NotBlank @Size(min = 20) String job_description,
        @NotEmpty List<@Valid CandidateInput> candidates) {
}