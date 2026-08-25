package com.mingo.backend.admin.dto;

import java.util.List;

public record AdminStatsResponse(
        long totalUsers,
        long newUsersThisWeek,
        long totalPosts,
        long postsToday,
        long totalForumMessages,
        long reportsPending,
        List<Long> userGrowth
) {
}
