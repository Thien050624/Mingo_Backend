package com.mingo.backend.chat.dto;

import java.util.List;
import java.util.UUID;

public record AddMembersRequest(List<UUID> memberIds) {
}
