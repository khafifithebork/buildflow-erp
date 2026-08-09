package com.buildflow.erp.domain.achats.dto.response;

import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.achats.entity.AchatStatut;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AchatResponse(
        UUID id,
        String ref,
        String fournisseurNom,
        String chantierNom,
        LocalDate dateCommande,
        LocalDate dateLivraisonPrevue,
        AchatStatut status, // Note: Mock data uses 'status', we map 'statut' to 'status'
        BigDecimal ht,
        BigDecimal tva,
        BigDecimal ttc,
        List<LigneAchatResponse> lignes,
        String bonLivraisonRef,
        String factureRef,
        /** How the order was settled; null until it reaches PAYE. */
        ModePaiement modePaiement,
        /** "L'achat a-t-il réellement servi au chantier ?" */
        boolean impactAnalytiqueChantier,
        /** "Y a-t-il une facture officielle à déclarer ?" */
        boolean impactComptableFiscal
) {}