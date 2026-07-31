package com.buildflow.erp.domain.comptabilite.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EcritureComptableResponse(
        String id,
        LocalDate date,
        String journal,
        String pieceRef,
        String libelle,
        List<EcritureLigneResponse> lignes,
        BigDecimal montant,
        String saisiePar
) {}