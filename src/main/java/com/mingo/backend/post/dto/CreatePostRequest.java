package com.mingo.backend.post.dto;

import com.mingo.backend.post.PostVisibility;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePostRequest(
        @Size(max = 5000) String content,
        List<@Size(max = 500) String> images,
        PostVisibility visibility
) {
}
