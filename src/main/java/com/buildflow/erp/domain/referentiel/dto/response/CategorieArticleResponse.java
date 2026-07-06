package com.buildflow.erp.domain.referentiel.dto.response;

import java.util.UUID;

public record CategorieArticleResponse(
        UUID id,
        String code,
        String libelle,
        UUID parentId
) {}