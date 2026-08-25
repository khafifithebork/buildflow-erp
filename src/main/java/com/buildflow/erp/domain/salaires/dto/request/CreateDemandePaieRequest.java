package com.buildflow.erp.domain.salaires.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateDemandePaieRequest(
        @NotBlank String libelle,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}", message = "Periode must be YYYY-MM format") String periode,
        UUID chantierId,
        UUID bpuLigneId,
        // Strictly positive: a demande for nothing is a data-entry slip, not a
        // disbursement, and it would still consume a reference number.
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal montantNet
) {}
