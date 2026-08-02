package com.buildflow.erp.domain.achats.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateLigneAchatRequest(
        @NotNull UUID articleId,
        @NotNull @DecimalMin("0.001") BigDecimal quantite,
        @NotNull @DecimalMin("0.0") BigDecimal prixUnitaire,
        UUID bpuLigneId
) {}