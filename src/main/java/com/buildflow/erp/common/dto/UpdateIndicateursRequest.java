package com.buildflow.erp.common.dto;

/**
 * Partial update of the two operational billing indicators carried by every
 * "operation" (Achat, CaisseTransaction).
 *
 * <p>Both fields are optional: a {@code null} leaves the current value
 * untouched, so a caller can flip one checkbox without knowing the other.
 */
public record UpdateIndicateursRequest(
        /** "L'achat a-t-il réellement servi au chantier ?" */
        Boolean impactAnalytiqueChantier,
        /** "Y a-t-il une facture officielle à déclarer ?" */
        Boolean impactComptableFiscal
) {}
