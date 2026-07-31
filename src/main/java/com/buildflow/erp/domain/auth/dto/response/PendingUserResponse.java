package com.buildflow.erp.domain.auth.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record PendingUserResponse(UUID id, String email, String role, LocalDateTime createdAt) {}