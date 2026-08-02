package com.buildflow.erp.domain.stock.dto.request;

import com.buildflow.erp.domain.stock.entity.TypeMouvement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMouvementStockRequest(
        @NotNull UUID articleId,
        @NotNull UUID chantierId,
        @NotNull TypeMouvement typeMouvement,
        @NotNull @DecimalMin("0.001") BigDecimal quantite,
        String documentRef
) {}
