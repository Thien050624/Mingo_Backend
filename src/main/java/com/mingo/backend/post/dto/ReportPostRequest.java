package com.mingo.backend.post.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportPostRequest(
        @NotBlank String reason
) {
}
