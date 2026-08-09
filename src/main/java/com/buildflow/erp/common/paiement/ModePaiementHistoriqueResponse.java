package com.buildflow.erp.common.paiement;

import java.time.LocalDateTime;
import java.util.UUID;

/** One line of the payment-mode audit trail. */
public record ModePaiementHistoriqueResponse(
        UUID id,
        ModePaiement ancienMode,
        ModePaiement nouveauMode,
        String modifiePar,
        LocalDateTime dateModification
) {
    public static ModePaiementHistoriqueResponse from(ModePaiementHistorique h) {
        return new ModePaiementHistoriqueResponse(
                h.getId(), h.getAncienMode(), h.getNouveauMode(), h.getModifiePar(), h.getCreatedAt());
    }
}
