package com.buildflow.erp.domain.soustraitance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateContratRequest(
        @NotNull UUID sousTraitantId,
        @NotNull UUID chantierId,
        @NotBlank String objet,
        @NotNull @DecimalMin("0.01") BigDecimal montantHt,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        UUID bpuLigneId
) {}
