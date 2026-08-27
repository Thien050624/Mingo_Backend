package com.mingo.backend.post;

import java.util.UUID;

public interface CommentReportCount {
    UUID getCommentId();
    long getReportCount();
}
