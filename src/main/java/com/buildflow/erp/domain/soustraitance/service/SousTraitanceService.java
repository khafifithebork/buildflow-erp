package com.buildflow.erp.domain.soustraitance.service;

import com.buildflow.erp.domain.soustraitance.dto.request.CreateContratRequest;
import com.buildflow.erp.domain.soustraitance.dto.request.CreatePaiementRequest;
import com.buildflow.erp.domain.soustraitance.dto.response.ContratSousTraitantResponse;
import com.buildflow.erp.domain.soustraitance.dto.response.PaiementSousTraitantResponse;

import java.util.List;
import java.util.UUID;

public interface SousTraitanceService {

    ContratSousTraitantResponse createContrat(CreateContratRequest request);

    ContratSousTraitantResponse findContratById(UUID id);

    List<ContratSousTraitantResponse> findAllContrats();

    List<ContratSousTraitantResponse> findContratsByChantier(UUID chantierId);

    /** Terminate a contract: EN_COURS → TERMINE */
    ContratSousTraitantResponse terminerContrat(UUID id);

    /** Create a payment against a contract */
    PaiementSousTraitantResponse createPaiement(UUID contratId, CreatePaiementRequest request);

    List<PaiementSousTraitantResponse> getPaiements(UUID contratId);

    /** Approve a payment: EN_ATTENTE → VALIDE */
    PaiementSousTraitantResponse validerPaiement(UUID paiementId);

    /** Pay: VALIDE → PAYE (triggers caisse debit + updates contract/sous-traitant totals) */
    PaiementSousTraitantResponse payerPaiement(UUID paiementId);
}
