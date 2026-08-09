package com.buildflow.erp.common.paiement;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.achats.entity.Achat;
import com.buildflow.erp.domain.achats.entity.AchatStatut;
import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.salaires.entity.FichePaie;
import com.buildflow.erp.domain.salaires.entity.FichePaieStatut;
import com.buildflow.erp.domain.salaires.repository.FichePaieRepository;
import com.buildflow.erp.domain.soustraitance.entity.PaiementSousTraitant;
import com.buildflow.erp.domain.soustraitance.entity.PaiementStatut;
import com.buildflow.erp.domain.soustraitance.repository.PaiementSousTraitantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Changes the payment mode of an already-settled document, and reads back the
 * audit trail. One service for all three document types so the rule and the
 * trail stay identical whichever module the change came from.
 *
 * <p><b>The caisse is deliberately not adjusted.</b> Switching a paid document
 * from CAISSE to VIREMENT does not credit the cash back, and switching the
 * other way does not debit it — that would silently move money on the back of
 * what is meant to be a correction to a label. The response says so, and the
 * trail records who changed what, so the treasury can be reconciled manually.
 */
@Service
@RequiredArgsConstructor
public class ModePaiementService {

    private final AchatRepository achatRepository;
    private final FichePaieRepository fichePaieRepository;
    private final PaiementSousTraitantRepository paiementRepository;
    private final ModePaiementAudit audit;

    @Transactional
    public ModePaiementResponse changer(TypeDocumentPaiement type, UUID documentId, ModePaiement nouveau) {
        return switch (type) {
            case ACHAT -> changerAchat(documentId, nouveau);
            case FICHE_PAIE -> changerFichePaie(documentId, nouveau);
            case PAIEMENT_SOUS_TRAITANT -> changerPaiementSousTraitant(documentId, nouveau);
        };
    }

    @Transactional(readOnly = true)
    public List<ModePaiementHistorique> historique(TypeDocumentPaiement type, UUID documentId) {
        return audit.historique(type, documentId);
    }

    private ModePaiementResponse changerAchat(UUID id, ModePaiement nouveau) {
        Achat achat = achatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achat", id));

        if (achat.getStatut() != AchatStatut.PAYE) {
            throw new BusinessRuleException(
                    "Le mode de paiement ne peut être modifié que sur une commande soldée (statut actuel : "
                            + achat.getStatut() + ").");
        }

        ModePaiement ancien = achat.getModePaiement();
        achat.setModePaiement(nouveau);
        achatRepository.save(achat);
        audit.record(TypeDocumentPaiement.ACHAT, id, achat.getRef(), ancien, nouveau);

        return new ModePaiementResponse(TypeDocumentPaiement.ACHAT, id, achat.getRef(),
                ancien, nouveau, avertissement(ancien, nouveau));
    }

    private ModePaiementResponse changerFichePaie(UUID id, ModePaiement nouveau) {
        FichePaie fiche = fichePaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FichePaie", id));

        if (fiche.getStatut() != FichePaieStatut.PAYEE) {
            throw new BusinessRuleException(
                    "Le mode de paiement ne peut être modifié que sur une fiche de paie payée (statut actuel : "
                            + fiche.getStatut() + ").");
        }

        ModePaiement ancien = fiche.getModePaiement();
        fiche.setModePaiement(nouveau);
        fichePaieRepository.save(fiche);
        audit.record(TypeDocumentPaiement.FICHE_PAIE, id, fiche.getReference(), ancien, nouveau);

        return new ModePaiementResponse(TypeDocumentPaiement.FICHE_PAIE, id, fiche.getReference(),
                ancien, nouveau, avertissement(ancien, nouveau));
    }

    private ModePaiementResponse changerPaiementSousTraitant(UUID id, ModePaiement nouveau) {
        PaiementSousTraitant paiement = paiementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaiementSousTraitant", id));

        if (paiement.getStatut() != PaiementStatut.PAYE) {
            throw new BusinessRuleException(
                    "Le mode de paiement ne peut être modifié que sur un paiement effectué (statut actuel : "
                            + paiement.getStatut() + ").");
        }

        ModePaiement ancien = paiement.getModePaiement();
        paiement.setModePaiement(nouveau);
        paiementRepository.save(paiement);
        audit.record(TypeDocumentPaiement.PAIEMENT_SOUS_TRAITANT, id, paiement.getReference(), ancien, nouveau);

        return new ModePaiementResponse(TypeDocumentPaiement.PAIEMENT_SOUS_TRAITANT, id, paiement.getReference(),
                ancien, nouveau, avertissement(ancien, nouveau));
    }

    /**
     * Flags the two directions that leave the caisse out of step with reality,
     * so whoever made the change knows a manual entry is owed.
     */
    private static String avertissement(ModePaiement ancien, ModePaiement nouveau) {
        if (ancien == ModePaiement.CAISSE && nouveau != ModePaiement.CAISSE) {
            return "La caisse avait été débitée pour ce document. Le solde n'a pas été recrédité "
                    + "automatiquement — enregistrez un crédit correctif si nécessaire.";
        }
        if (ancien != ModePaiement.CAISSE && nouveau == ModePaiement.CAISSE) {
            return "La caisse n'avait pas été débitée pour ce document. Le solde n'a pas été débité "
                    + "automatiquement — enregistrez un débit correctif si nécessaire.";
        }
        return null;
    }
}
