package com.buildflow.erp.domain.achats.service;

import com.buildflow.erp.common.code.CodeGenerator;
import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.common.paiement.ModePaiementAudit;
import com.buildflow.erp.common.paiement.TypeDocumentPaiement;
import com.buildflow.erp.common.code.CodeSequence;
import com.buildflow.erp.common.dto.UpdateIndicateursRequest;
import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.UpdateLignePrixRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.entity.Achat;
import com.buildflow.erp.domain.achats.entity.AchatStatut;
import com.buildflow.erp.domain.achats.entity.LigneAchat;
import com.buildflow.erp.domain.achats.mapper.AchatMapper;
import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.buildflow.erp.domain.bpu.repository.BpuLigneRepository;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.referentiel.repository.FournisseurRepository;
import com.buildflow.erp.domain.stock.service.StockService;
import com.buildflow.erp.domain.tresorerie.service.TresorerieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AchatServiceImpl implements AchatService {

    private final AchatRepository achatRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ChantierRepository chantierRepository;
    private final ArticleRepository articleRepository;
    private final BpuLigneRepository bpuLigneRepository;
    private final AchatMapper achatMapper;
    private final CodeGenerator codeGenerator;
    private final StockService stockService;
    private final TresorerieService tresorerieService;
    private final ModePaiementAudit modePaiementAudit;

    private static final BigDecimal TVA_RATE = new BigDecimal("0.20"); // 20% Moroccan TVA

    @Override
    @Transactional
    public AchatResponse create(CreateAchatRequest request) {
        Fournisseur fournisseur = fournisseurRepository.findById(request.fournisseurId())
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", request.fournisseurId()));

        Chantier chantier = chantierRepository.findById(request.chantierId())
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", request.chantierId()));

        Achat achat = new Achat();
        achat.setRef(codeGenerator.next(CodeSequence.ACHAT));
        achat.setFournisseur(fournisseur);
        achat.setChantier(chantier);
        achat.setDateCommande(request.dateCommande());
        achat.setDateLivraisonPrevue(request.dateLivraisonPrevue());
        achat.setImpactAnalytiqueChantier(Boolean.TRUE.equals(request.impactAnalytiqueChantier()));
        achat.setImpactComptableFiscal(Boolean.TRUE.equals(request.impactComptableFiscal()));

        BigDecimal totalHt = BigDecimal.ZERO;

        for (CreateLigneAchatRequest ligneReq : request.lignes()) {
            Article article = articleRepository.findById(ligneReq.articleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Article", ligneReq.articleId()));

            LigneAchat ligne = new LigneAchat();
            ligne.setAchat(achat);
            ligne.setArticle(article);
            ligne.setDesignation(article.getDesignation()); // Snapshot
            ligne.setUnite(article.getUnite());             // Snapshot
            ligne.setQuantite(ligneReq.quantite());
            ligne.setPrixUnitaire(ligneReq.prixUnitaire());

            if (ligneReq.bpuLigneId() != null) {
                BpuLigne bpuLigne = bpuLigneRepository.findById(ligneReq.bpuLigneId())
                        .orElseThrow(() -> new ResourceNotFoundException("BpuLigne", ligneReq.bpuLigneId()));
                ligne.setBpuLigne(bpuLigne);
            }

            // Price is a double now; convert once for the exact total.
            BigDecimal ligneTotal = ligneReq.quantite()
                    .multiply(BigDecimal.valueOf(ligneReq.prixUnitaire()))
                    .setScale(2, RoundingMode.HALF_UP);
            ligne.setTotal(ligneTotal);

            totalHt = totalHt.add(ligneTotal);
            achat.getLignes().add(ligne);
        }

        BigDecimal tva = totalHt.multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ttc = totalHt.add(tva);

        achat.setHt(totalHt);
        achat.setTva(tva);
        achat.setTtc(ttc);

        Achat saved = achatRepository.save(achat);
        return achatMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AchatResponse findById(UUID id) {
        Achat achat = achatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achat", id));
        return achatMapper.toResponse(achat);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchatResponse> findAll() {
        return achatRepository.findAll().stream()
                .map(achatMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AchatResponse validateBL(UUID id, String bonLivraisonRef) {
        Achat achat = findEntityById(id);
        assertStatus(achat, AchatStatut.EN_COURS, "LIVRE");

        achat.setStatut(AchatStatut.LIVRE);
        achat.setBonLivraisonRef(bonLivraisonRef);

        Achat savedAchat = achatRepository.save(achat);

        // CROSS-DOMAIN SIDE EFFECT: Increment Stock
        stockService.approvisionnerDepuisAchat(savedAchat);

        return achatMapper.toResponse(savedAchat);
    }

    @Override
    @Transactional
    public AchatResponse validateFacture(UUID id, String factureRef) {
        Achat achat = findEntityById(id);
        assertStatus(achat, AchatStatut.LIVRE, "FACTURE");

        achat.setStatut(AchatStatut.FACTURE);
        achat.setFactureRef(factureRef);

        return achatMapper.toResponse(achatRepository.save(achat));
    }

    @Override
    @Transactional
    public AchatResponse validatePaiement(UUID id, ModePaiement modePaiement) {
        Achat achat = findEntityById(id);
        assertStatus(achat, AchatStatut.FACTURE, "PAYE");

        achat.setStatut(AchatStatut.PAYE);
        achat.setModePaiement(modePaiement);
        modePaiementAudit.record(TypeDocumentPaiement.ACHAT, achat.getId(),
                achat.getRef(), null, modePaiement);

        // CROSS-DOMAIN SIDE EFFECT: only cash settles out of the caisse. A
        // virement, cheque or effet clears through the bank and must leave the
        // chantier's cash balance untouched.
        if (modePaiement == ModePaiement.CAISSE) {
            tresorerieService.debiterPourAchat(
                    achat.getChantier().getId(), achat.getTtc(), achat.getRef(),
                    achat.isImpactAnalytiqueChantier(), achat.isImpactComptableFiscal());
        }

        return achatMapper.toResponse(achatRepository.save(achat));
    }

    @Override
    @Transactional
    public AchatResponse updateIndicateurs(UUID id, UpdateIndicateursRequest request) {
        Achat achat = findEntityById(id);

        // Null = "leave unchanged", so a single checkbox can be toggled alone.
        if (request.impactAnalytiqueChantier() != null) {
            achat.setImpactAnalytiqueChantier(request.impactAnalytiqueChantier());
        }
        if (request.impactComptableFiscal() != null) {
            achat.setImpactComptableFiscal(request.impactComptableFiscal());
        }

        return achatMapper.toResponse(achatRepository.save(achat));
    }

    /**
     * Re-prices one line of a purchase order and rolls the change up into the
     * order's HT / TVA / TTC.
     *
     * <p>Allowed at every statut, by product decision. Two consequences the
     * caller has to live with, and which {@link #repricingWarning} surfaces
     * rather than hiding:
     * <ul>
     *   <li>at {@code FACTURE}, the recorded invoice total no longer matches
     *       the order;</li>
     *   <li>at {@code PAYE}, the caisse was already debited for the old TTC, so
     *       the cash ledger is now short or over by the difference and needs a
     *       manual adjusting entry.</li>
     * </ul>
     * Stock is unaffected — provisioning keys off quantity, not price.
     */
    @Override
    @Transactional
    public AchatResponse updateLignePrix(UUID achatId, UUID ligneId, UpdateLignePrixRequest request) {
        Achat achat = findEntityById(achatId);

        LigneAchat ligne = achat.getLignes().stream()
                .filter(l -> l.getId().equals(ligneId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("LigneAchat", ligneId));

        ligne.setPrixUnitaire(request.prixUnitaire());
        ligne.setTotal(ligne.getQuantite()
                .multiply(BigDecimal.valueOf(request.prixUnitaire()))
                .setScale(2, RoundingMode.HALF_UP));

        recomputeTotals(achat);

        return achatMapper.toResponse(achatRepository.save(achat));
    }

    /**
     * Message warning that re-pricing has left something downstream out of step,
     * or {@code null} when nothing downstream has happened yet.
     */
    public static String repricingWarning(AchatStatut statut) {
        return switch (statut) {
            case EN_COURS, LIVRE -> null;
            case FACTURE -> "Prix mis à jour. La facture déjà enregistrée pour cette commande "
                    + "ne correspond plus au nouveau montant : vérifiez-la.";
            case PAYE -> "Prix mis à jour. Cette commande était déjà payée : la caisse a été débitée "
                    + "de l'ancien montant TTC. Enregistrez une opération d'ajustement pour la différence.";
        };
    }

    /** Recomputes HT / TVA / TTC from the order's lines. */
    private void recomputeTotals(Achat achat) {
        BigDecimal totalHt = achat.getLignes().stream()
                .map(LigneAchat::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tva = totalHt.multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);

        achat.setHt(totalHt);
        achat.setTva(tva);
        achat.setTtc(totalHt.add(tva));
    }

    private Achat findEntityById(UUID id) {
        return achatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achat", id));
    }

    private void assertStatus(Achat achat, AchatStatut expectedCurrent, String nextAction) {
        if (achat.getStatut() != expectedCurrent) {
            throw new BusinessRuleException(
                    String.format("Cannot %s an Achat that is currently '%s'. Expected status: '%s'",
                            nextAction, achat.getStatut(), expectedCurrent)
            );
        }
    }
}