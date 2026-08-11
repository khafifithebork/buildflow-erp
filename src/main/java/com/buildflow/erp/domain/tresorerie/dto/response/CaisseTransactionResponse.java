package com.buildflow.erp.domain.tresorerie.dto.response;

import com.buildflow.erp.domain.tresorerie.entity.TypeTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaisseTransactionResponse(
        UUID id,
        TypeTransaction typeTransaction,
        BigDecimal montant,
        String motif,
        String referenceDocument,
        LocalDateTime createdAt,
        String bpuLigneRef,
        /** True once cancelled; the reversing entry carries the correction. */
        boolean annule,
        /** True when this row IS a correction of another. */
        boolean ajustement,
        /** "L'achat a-t-il réellement servi au chantier ?" */
        boolean impactAnalytiqueChantier,
        /** "Y a-t-il une facture officielle à déclarer ?" */
        boolean impactComptableFiscal
) {}
