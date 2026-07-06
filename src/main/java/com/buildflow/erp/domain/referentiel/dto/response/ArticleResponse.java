package com.buildflow.erp.domain.referentiel.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ArticleResponse(
        UUID id,
        String code,
        String designation,
        String description,
        UUID categorieId,
        String categorieLibelle,
        String unite,
        BigDecimal prixAchatRef,
        BigDecimal tvaRate,
        boolean actif,
        List<String> fournisseursPreferentiels
) {}