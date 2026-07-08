package com.buildflow.erp.domain.tresorerie.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCaisseRequest(
        @NotBlank String code,
        @NotBlank String libelle,
        @NotNull UUID chantierId,
        @NotNull @DecimalMin("0.00") BigDecimal seuilMinimum
) {}
