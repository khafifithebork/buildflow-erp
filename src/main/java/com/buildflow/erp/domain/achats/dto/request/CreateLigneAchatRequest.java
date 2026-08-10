package com.buildflow.erp.domain.achats.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record CreateLigneAchatRequest(
        @NotNull UUID articleId,
        @NotNull @Positive Double quantite,
        @NotNull @PositiveOrZero Double prixUnitaire,
        UUID bpuLigneId
) {}