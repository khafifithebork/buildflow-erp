package com.buildflow.erp.common.paiement;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.service.AchatService;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.entity.*;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.FournisseurRepository;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
import com.buildflow.erp.domain.tresorerie.repository.CaisseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payment mode across the three payable documents.
 *
 * <p>The rule that matters: only CAISSE moves the chantier's cash balance.
 * Everything else settles through the bank and must leave it alone.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ModePaiementTests {

    @Autowired AchatService achatService;
    @Autowired ChantierService chantierService;
    @Autowired CaisseRepository caisseRepository;
    @Autowired FournisseurRepository fournisseurRepository;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;
    @Autowired ModePaiementService modePaiementService;
    @Autowired ModePaiementAudit modePaiementAudit;

    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * The headline change: a virement settles an order even with an empty
     * caisse. Before, every payment went through the caisse and this was a 422.
     */
    @Test
    void aVirementSettlesWithoutTouchingTheCaisse() {
        Fixture f = newOrderAtFacture();

        AchatResponse paid = achatService.validatePaiement(f.achatId, ModePaiement.VIREMENT);

        assertThat(paid.status().name()).isEqualTo("PAYE");
        assertThat(paid.modePaiement()).isEqualTo(ModePaiement.VIREMENT);
        assertThat(caisseRepository.findById(f.caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void chequeAndEffetAlsoLeaveTheCaisseAlone() {
        for (ModePaiement mode : List.of(ModePaiement.CHEQUE, ModePaiement.EFFET)) {
            Fixture f = newOrderAtFacture();

            AchatResponse paid = achatService.validatePaiement(f.achatId, mode);

            assertThat(paid.modePaiement()).isEqualTo(mode);
            assertThat(caisseRepository.findById(f.caisseId).orElseThrow().getSolde())
                    .isEqualByComparingTo("0.00");
        }
    }

    /** CAISSE keeps its old behaviour, including the insufficient-funds guard. */
    @Test
    void caisseStillDebitsAndStillRefusesWhenShort() {
        Fixture f = newOrderAtFacture();

        assertThatThrownBy(() -> achatService.validatePaiement(f.achatId, ModePaiement.CAISSE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient funds");
    }

    /** Every assignment lands in the trail, including the first one. */
    @Test
    void theFirstAssignmentIsRecordedInTheTrail() {
        Fixture f = newOrderAtFacture();
        achatService.validatePaiement(f.achatId, ModePaiement.CHEQUE);

        var trail = modePaiementAudit.historique(TypeDocumentPaiement.ACHAT, f.achatId);

        assertThat(trail).hasSize(1);
        assertThat(trail.getFirst().getAncienMode()).isNull();
        assertThat(trail.getFirst().getNouveauMode()).isEqualTo(ModePaiement.CHEQUE);
    }

    @Test
    void changingTheModeAppendsToTheTrailNewestFirst() {
        Fixture f = newOrderAtFacture();
        achatService.validatePaiement(f.achatId, ModePaiement.VIREMENT);

        modePaiementService.changer(TypeDocumentPaiement.ACHAT, f.achatId, ModePaiement.CHEQUE);
        modePaiementService.changer(TypeDocumentPaiement.ACHAT, f.achatId, ModePaiement.EFFET);

        var trail = modePaiementAudit.historique(TypeDocumentPaiement.ACHAT, f.achatId);

        assertThat(trail).hasSize(3);
        assertThat(trail.get(0).getNouveauMode()).isEqualTo(ModePaiement.EFFET);
        assertThat(trail.get(0).getAncienMode()).isEqualTo(ModePaiement.CHEQUE);
        assertThat(trail.get(2).getAncienMode()).isNull();
    }

    /** Re-selecting the same mode is not a change and must not pad the trail. */
    @Test
    void reSelectingTheSameModeDoesNotAppend() {
        Fixture f = newOrderAtFacture();
        achatService.validatePaiement(f.achatId, ModePaiement.CHEQUE);

        modePaiementService.changer(TypeDocumentPaiement.ACHAT, f.achatId, ModePaiement.CHEQUE);

        assertThat(modePaiementAudit.historique(TypeDocumentPaiement.ACHAT, f.achatId)).hasSize(1);
    }

    /**
     * Switching a paid order off CAISSE does not silently credit the cash back;
     * the caller is told a corrective entry is owed.
     */
    @Test
    void switchingAwayFromCaisseWarnsInsteadOfMovingMoney() {
        Fixture f = newOrderAtFacture();
        achatService.validatePaiement(f.achatId, ModePaiement.VIREMENT);

        ModePaiementResponse toCaisse =
                modePaiementService.changer(TypeDocumentPaiement.ACHAT, f.achatId, ModePaiement.CAISSE);
        assertThat(toCaisse.avertissement()).contains("n'avait pas été débitée");

        ModePaiementResponse backToBank =
                modePaiementService.changer(TypeDocumentPaiement.ACHAT, f.achatId, ModePaiement.VIREMENT);
        assertThat(backToBank.avertissement()).contains("recrédité");

        // Neither direction moved the balance.
        assertThat(caisseRepository.findById(f.caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("0.00");
    }

    /** The mode is a property of a settled document, not of a pending one. */
    @Test
    void theModeCannotBeChangedBeforeTheOrderIsPaid() {
        Fixture f = newOrderAtFacture();

        assertThatThrownBy(() ->
                modePaiementService.changer(TypeDocumentPaiement.ACHAT, f.achatId, ModePaiement.CHEQUE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("commande soldée");
    }

    /** An unpaid order carries no mode — CAISSE is no longer an implicit default. */
    @Test
    void anUnpaidOrderHasNoMode() {
        assertThat(achatService.findById(newOrderAtFacture().achatId).modePaiement()).isNull();
    }

    // ── Fixture ───────────────────────────────────────────────────────

    private record Fixture(UUID achatId, UUID caisseId) {}

    private Fixture newOrderAtFacture() {
        String suffix = "MP-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        UUID chantierId = chantierService.create(new CreateChantierRequest(
                "Chantier " + suffix, "Client", "Adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(6),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();

        Fournisseur fournisseur = new Fournisseur();
        fournisseur.setCode("F-" + suffix);
        fournisseur.setRaisonSociale("Fournisseur " + suffix);
        fournisseur.setIce("ICE" + suffix);
        fournisseur.setStatut(FournisseurStatut.ACTIF);
        fournisseur = fournisseurRepository.save(fournisseur);

        CategorieArticle categorie = categorieArticleRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategorieArticle c = new CategorieArticle();
                    c.setCode("MPC" + (SEQ.get() % 1000));
                    c.setLibelle("Catégorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-" + suffix);
        article.setDesignation("Article de test");
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(10.0);
        article.setTvaRate(new BigDecimal("20.00"));
        article = articleRepository.save(article);

        AchatResponse achat = achatService.create(new CreateAchatRequest(
                fournisseur.getId(), chantierId, LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(article.getId(), 10.0, 10.0, null)),
                null, null));

        achatService.validateBL(achat.id(), "BL-" + suffix);
        achatService.validateFacture(achat.id(), "FA-" + suffix);

        UUID caisseId = caisseRepository.findByChantierId(chantierId).getFirst().getId();
        return new Fixture(achat.id(), caisseId);
    }
}
