package com.buildflow.erp.domain.attachement.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAttachementLigneRequest(
        @NotNull UUID bpuLigneId,
        @NotNull @DecimalMin("0.000") BigDecimal nouveauCumul
) {}
