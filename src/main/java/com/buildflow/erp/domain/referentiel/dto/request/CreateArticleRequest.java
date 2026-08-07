package com.buildflow.erp.domain.referentiel.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateArticleRequest(
        @NotBlank String designation,
        String description,
        @NotNull UUID categorieId,
        @NotBlank String unite,
        @NotNull @PositiveOrZero Double prixAchatRef,
        @NotNull @DecimalMin("0.0") BigDecimal tvaRate,
        List<String> fournisseursPreferentiels
) {}