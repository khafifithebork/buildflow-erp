package com.buildflow.erp.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank String currentPassword
) {}
