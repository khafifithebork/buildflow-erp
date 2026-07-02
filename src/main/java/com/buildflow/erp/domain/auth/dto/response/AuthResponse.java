package com.buildflow.erp.domain.auth.dto.response;

public record AuthResponse(String accessToken, String email, String role) {}