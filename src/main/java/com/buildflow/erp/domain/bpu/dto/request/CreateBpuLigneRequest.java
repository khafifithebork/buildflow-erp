package com.buildflow.erp.domain.bpu.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateBpuLigneRequest(
        @NotBlank String ref,
        @NotBlank String designation,
        @NotBlank String unite,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal qtePrevue,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal puHt
) {}
