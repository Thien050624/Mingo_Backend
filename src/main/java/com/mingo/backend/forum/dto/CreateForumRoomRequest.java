package com.mingo.backend.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateForumRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 300) String description
) {
}
