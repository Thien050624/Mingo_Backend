package com.mingo.backend.post;

import java.util.UUID;

public interface CommentLikeCount {
    UUID getCommentId();
    long getLikeCount();
}
