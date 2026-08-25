package com.mingo.backend.post.dto;

import com.mingo.backend.post.ReactionType;
import jakarta.validation.constraints.NotNull;

public record ReactionRequest(@NotNull ReactionType type) {
}
