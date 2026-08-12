package com.buildflow.erp.domain.achats.service;

import com.buildflow.erp.common.fiscal.Tva;
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

            // Quantity and price are both doubles; convert each once and do the
            // multiplication in BigDecimal so the invoiced total stays exact.
            BigDecimal ligneTotal = BigDecimal.valueOf(ligneReq.quantite())
                    .multiply(BigDecimal.valueOf(ligneReq.prixUnitaire()))
                    .setScale(2, RoundingMode.HALF_UP);
            ligne.setTotal(ligneTotal);

            totalHt = totalHt.add(ligneTotal);
            achat.getLignes().add(ligne);
        }

        BigDecimal tva = Tva.sur(totalHt);
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
            tresorerieService.debiterPourDocument(
                    achat.getChantier().getId(), TypeDocumentPaiement.ACHAT,
                    achat.getId(), achat.getTtc(), achat.getRef(),
                    achat.isImpactAnalytiqueChantier(), achat.isImpactComptableFiscal());
        }

        return achatMapper.toResponse(achatRepository.save(achat));
    }

    /**
     * Défait un paiement enregistré à tort : la commande redevient une facture
     * à régler, et la caisse récupère ce qui en était sorti.
     *
     * <p>C'est le seul chemin pour annuler le règlement d'une commande. Contre-
     * passer l'écriture depuis la caisse ne suffit pas — l'argent reviendrait
     * en laissant la commande payée, donc sans dette et sans décaissement : la
     * commande deviendrait gratuite au bilan. Les deux faces se défont
     * ensemble ou pas du tout, ce que la transaction garantit ici.
     *
     * <p>Le retour se fait à {@code FACTURE}, l'état d'où venait le paiement.
     * La réception et le stock ne sont pas touchés : la marchandise est bien
     * arrivée, c'est le règlement qui n'aurait pas dû être enregistré.
     */
    @Override
    @Transactional
    public AchatResponse annulerPaiement(UUID id, String motif) {
        Achat achat = findEntityById(id);
        if (achat.getStatut() != AchatStatut.PAYE) {
            throw new BusinessRuleException(
                    "Cette commande n'est pas payée : il n'y a pas de paiement à annuler.");
        }

        ModePaiement modeAnnule = achat.getModePaiement();
        String raison = motif != null && !motif.isBlank() ? motif : "paiement annulé";

        achat.setStatut(AchatStatut.FACTURE);
        achat.setModePaiement(null);
        modePaiementAudit.record(TypeDocumentPaiement.ACHAT, achat.getId(),
                achat.getRef(), modeAnnule, null);

        // Seul un règlement en espèces est sorti de la caisse ; un virement, un
        // chèque ou un effet se dénoue à la banque et ne laisse rien à rendre ici.
        if (modeAnnule == ModePaiement.CAISSE) {
            tresorerieService.annulerEcrituresDuDocument(achat.getId(), raison);
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
     * <p>Allowed at every statut, by product decision. What that leaves out of
     * step is corrected rather than hidden: goods already received are re-valued
     * in stock, and an order already settled from the caisse gets an adjusting
     * entry for the difference. {@link #repricingWarning} says what remains for
     * the user to deal with — an invoice that no longer matches, or stock the
     * correction could not reach.
     */
    @Override
    @Transactional
    public RepricingResult updateLignePrix(UUID achatId, UUID ligneId, UpdateLignePrixRequest request) {
        Achat achat = findEntityById(achatId);

        LigneAchat ligne = achat.getLignes().stream()
                .filter(l -> l.getId().equals(ligneId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("LigneAchat", ligneId));

        BigDecimal ttcAvant = achat.getTtc();
        double prixAvant = ligne.getPrixUnitaire();

        appliquerPrix(ligne, request.prixUnitaire());
        Revalorisation revalorisation = revaloriserStock(achat, ligne, prixAvant, request.prixUnitaire());

        return finaliserRepricing(achat, ttcAvant, revalorisation);
    }

    /**
     * Re-prices a whole order to a new total HT — the case where a supplier
     * renegotiates the order rather than one article of it.
     *
     * <p>The new total is spread over the lines in the proportions they already
     * have, so their relative weight is preserved and the line prices stay the
     * figures everything downstream is computed from. Rounding is absorbed by
     * the last line, so the lines always add up to exactly what was asked for.
     */
    @Override
    @Transactional
    public RepricingResult updateMontantHt(UUID achatId, BigDecimal montantHt) {
        if (montantHt == null || montantHt.signum() < 0) {
            throw new BusinessRuleException("Le montant HT doit être positif.");
        }

        Achat achat = findEntityById(achatId);
        List<LigneAchat> lignes = achat.getLignes();
        if (lignes.isEmpty()) {
            throw new BusinessRuleException("Cette commande n'a aucune ligne à re-tarifer.");
        }

        BigDecimal htAvant = achat.getHt();
        if (htAvant == null || htAvant.signum() == 0) {
            throw new BusinessRuleException(
                    "Cette commande est à 0 DH : il n'y a pas de répartition à conserver. "
                            + "Modifiez le prix ligne par ligne.");
        }

        BigDecimal ttcAvant = achat.getTtc();
        BigDecimal cible = montantHt.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cumul = BigDecimal.ZERO;
        Revalorisation revalorisation = Revalorisation.COMPLETE;

        for (int i = 0; i < lignes.size(); i++) {
            LigneAchat ligne = lignes.get(i);
            // The last line takes whatever is left, so the parts sum to the
            // target exactly instead of drifting by a centime per line.
            BigDecimal totalLigne = i == lignes.size() - 1
                    ? cible.subtract(cumul)
                    : cible.multiply(ligne.getTotal()).divide(htAvant, 2, RoundingMode.HALF_UP);
            cumul = cumul.add(totalLigne);

            double prixAvant = ligne.getPrixUnitaire();
            double prixApres = totalLigne
                    .divide(BigDecimal.valueOf(ligne.getQuantite()), 6, RoundingMode.HALF_UP)
                    .doubleValue();

            appliquerPrix(ligne, prixApres);
            revalorisation = pireDes(revalorisation, revaloriserStock(achat, ligne, prixAvant, prixApres));
        }

        return finaliserRepricing(achat, ttcAvant, revalorisation);
    }

    /**
     * The order as a whole is only fully corrected if every line was. One line
     * short of stock makes the whole re-pricing partial, since the user is
     * being told about the commande, not about a line.
     */
    private static Revalorisation pireDes(Revalorisation cumul, Revalorisation ligne) {
        if (cumul == ligne) {
            return cumul;
        }
        return Revalorisation.PARTIELLE;
    }

    /** Sets a line's unit price and its total, which is derived from it. */
    private static void appliquerPrix(LigneAchat ligne, double prixUnitaire) {
        ligne.setPrixUnitaire(prixUnitaire);
        ligne.setTotal(BigDecimal.valueOf(ligne.getQuantite())
                .multiply(BigDecimal.valueOf(prixUnitaire))
                .setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Goods received are already sitting in stock at the price they came in on.
     * Re-pricing the order without re-valuing them leaves the stock short by the
     * difference, and the marge nette reads that shortfall as a loss that never
     * happened — the écart of qté × Δprix users were reporting.
     *
     * <p>Only the units still held are corrected. What was consumed left at the
     * old price and stays there.
     */
    private Revalorisation revaloriserStock(Achat achat, LigneAchat ligne, double prixAvant, double prixApres) {
        if (achat.getStatut() == AchatStatut.EN_COURS || Double.compare(prixAvant, prixApres) == 0) {
            return Revalorisation.COMPLETE;   // nothing received yet, or nothing changed
        }

        BigDecimal livree = BigDecimal.valueOf(ligne.getQuantite()).setScale(3, RoundingMode.HALF_UP);
        BigDecimal corrigee = stockService.revaloriser(
                ligne.getArticle().getId(),
                achat.getChantier().getId(),
                livree,
                prixApres - prixAvant);

        if (corrigee.signum() <= 0) {
            return Revalorisation.IMPOSSIBLE;
        }
        return corrigee.compareTo(livree) >= 0 ? Revalorisation.COMPLETE : Revalorisation.PARTIELLE;
    }

    /** Rolls the new line prices up, reconciles the caisse, and saves. */
    private RepricingResult finaliserRepricing(Achat achat, BigDecimal ttcAvant, Revalorisation revalorisation) {
        recomputeTotals(achat);

        // An order already settled from the caisse has moved real money. Leaving
        // the ledger on the old amount while the order reports the new one makes
        // every derived figure — the marges above all — disagree with the cash
        // that actually left. Post the difference as its own movement so the two
        // stay reconciled. Other payment modes clear outside the caisse, so
        // there is nothing here to correct.
        if (achat.getStatut() == AchatStatut.PAYE && achat.getModePaiement() == ModePaiement.CAISSE) {
            tresorerieService.ajusterPourAchat(
                    achat.getChantier().getId(),
                    achat.getId(),
                    achat.getTtc().subtract(ttcAvant),
                    achat.getRef(),
                    achat.isImpactAnalytiqueChantier(),
                    achat.isImpactComptableFiscal());
        }

        Achat saved = achatRepository.save(achat);
        return new RepricingResult(achatMapper.toResponse(saved),
                repricingWarning(saved.getStatut(), revalorisation));
    }

    /**
     * Message warning that re-pricing has left something downstream out of step,
     * or {@code null} when nothing needs saying.
     */
    public static String repricingWarning(AchatStatut statut, Revalorisation revalorisation) {
        String surStatut = switch (statut) {
            case EN_COURS, LIVRE -> null;
            case FACTURE -> "Prix mis à jour. La facture déjà enregistrée pour cette commande "
                    + "ne correspond plus au nouveau montant : vérifiez-la.";
            // The adjusting entry is posted automatically; say so rather than
            // asking for a correction that has already happened.
            case PAYE -> "Prix mis à jour. Cette commande était déjà payée : la différence a été "
                    + "passée en écriture d'ajustement sur la caisse du chantier.";
        };

        String surStock = switch (revalorisation) {
            case COMPLETE -> null;
            case PARTIELLE -> "Une partie de la marchandise a déjà été consommée : "
                    + "seul le stock restant a été revalorisé, l'écart sur le reste est un coût passé.";
            case IMPOSSIBLE -> "La marchandise reçue sur cette commande n'est plus en stock : "
                    + "sa valeur n'a pas pu être corrigée.";
        };

        if (surStock == null) {
            return surStatut;
        }
        return surStatut == null ? "Prix mis à jour. " + surStock : surStatut + " " + surStock;
    }

    /** Recomputes HT / TVA / TTC from the order's lines. */
    private void recomputeTotals(Achat achat) {
        BigDecimal totalHt = achat.getLignes().stream()
                .map(LigneAchat::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tva = Tva.sur(totalHt);

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