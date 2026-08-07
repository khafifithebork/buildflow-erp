package com.buildflow.erp.domain.achats.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateAchatRequest(
        @NotNull UUID fournisseurId,
        @NotNull UUID chantierId,
        @NotNull LocalDate dateCommande,
        @NotNull LocalDate dateLivraisonPrevue,
        @NotEmpty @Valid List<CreateLigneAchatRequest> lignes,

        // Operational billing indicators. Boxed + optional so clients written
        // before these existed keep working; null is read as false.
        /** "L'achat a-t-il réellement servi au chantier ?" */
        Boolean impactAnalytiqueChantier,
        /** "Y a-t-il une facture officielle à déclarer ?" */
        Boolean impactComptableFiscal
) {}