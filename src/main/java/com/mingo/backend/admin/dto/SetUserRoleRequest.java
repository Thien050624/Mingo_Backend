package com.mingo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record SetUserRoleRequest(@NotBlank String role) {
}
