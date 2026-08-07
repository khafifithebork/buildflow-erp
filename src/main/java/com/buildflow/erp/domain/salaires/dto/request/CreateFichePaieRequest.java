package com.buildflow.erp.domain.salaires.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateFichePaieRequest(
        @NotNull UUID employeId,
        @NotNull UUID chantierId,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}", message = "Periode must be YYYY-MM format") String periode,
        @Min(0) int joursTravailles,
        @NotNull @DecimalMin("0.00") BigDecimal salaireBase,
        @DecimalMin("0.00") BigDecimal heuresSupplementaires,
        @DecimalMin("0.00") BigDecimal montantHeuresSupp,
        @DecimalMin("0.00") BigDecimal primeTransport,
        @DecimalMin("0.00") BigDecimal primePanier,
        @DecimalMin("0.00") BigDecimal autresPrimes,
        @DecimalMin("0.00") BigDecimal avance,
        @DecimalMin("0.00") BigDecimal deductionsCnss,
        @DecimalMin("0.00") BigDecimal deductionsIr,
        UUID bpuLigneId
) {}
