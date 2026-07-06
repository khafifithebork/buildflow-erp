package com.buildflow.erp.domain.referentiel.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateCategorieArticleRequest(
        @NotBlank String code,
        @NotBlank String libelle,
        UUID parentId
) {}