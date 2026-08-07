package com.buildflow.erp.domain.tresorerie.dto.request;

import com.buildflow.erp.domain.tresorerie.entity.TypeTransaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull TypeTransaction typeTransaction,
        @NotNull @DecimalMin(value = "0.01", message = "Montant must be positive") BigDecimal montant,
        @NotBlank String motif,
        String referenceDocument,
        UUID bpuLigneId,

        // Operational billing indicators. Boxed + optional so clients written
        // before these existed keep working; null is read as false.
        /** "L'achat a-t-il réellement servi au chantier ?" */
        Boolean impactAnalytiqueChantier,
        /** "Y a-t-il une facture officielle à déclarer ?" */
        Boolean impactComptableFiscal
) {}
