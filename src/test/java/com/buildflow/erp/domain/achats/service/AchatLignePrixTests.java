package com.buildflow.erp.domain.achats.service;

import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.UpdateLignePrixRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.entity.AchatStatut;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import com.buildflow.erp.domain.referentiel.entity.FournisseurStatut;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.FournisseurRepository;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
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
 * Re-pricing an order line. Runs against a LOCAL throwaway Postgres; every row
 * is rolled back by {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AchatLignePrixTests {

    @Autowired AchatService achatService;
    @Autowired ChantierService chantierService;
    @Autowired FournisseurRepository fournisseurRepository;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Test
    void repricingALineRecomputesTheLineTotalAndTheOrderTotals() {
        // 2 × 100 = 200 HT, +20% TVA = 240 TTC
        AchatResponse achat = createAchat(new BigDecimal("2"), 100.00);
        assertThat(achat.ht()).isEqualByComparingTo("200.00");
        assertThat(achat.ttc()).isEqualByComparingTo("240.00");

        UUID ligneId = achat.lignes().getFirst().id();

        // Re-price to 250 → 2 × 250 = 500 HT, TVA 100, TTC 600
        AchatResponse updated = achatService.updateLignePrix(
                achat.id(), ligneId, new UpdateLignePrixRequest(250.00));

        assertThat(updated.lignes().getFirst().prixUnitaire()).isEqualTo(250.00);
        assertThat(updated.lignes().getFirst().total()).isEqualByComparingTo("500.00");
        assertThat(updated.ht()).isEqualByComparingTo("500.00");
        assertThat(updated.tva()).isEqualByComparingTo("100.00");
        assertThat(updated.ttc()).isEqualByComparingTo("600.00");
    }

    @Test
    void repricingSurvivesAReload() {
        AchatResponse achat = createAchat(new BigDecimal("3"), 10.00);
        UUID ligneId = achat.lignes().getFirst().id();

        achatService.updateLignePrix(achat.id(), ligneId, new UpdateLignePrixRequest(11.50));

        AchatResponse reloaded = achatService.findById(achat.id());
        assertThat(reloaded.lignes().getFirst().prixUnitaire()).isEqualTo(11.50);
        assertThat(reloaded.ht()).isEqualByComparingTo("34.50");
    }

    @Test
    void repricingToZeroIsAllowedAndZeroesTheOrder() {
        AchatResponse achat = createAchat(new BigDecimal("4"), 25.00);
        UUID ligneId = achat.lignes().getFirst().id();

        AchatResponse updated = achatService.updateLignePrix(
                achat.id(), ligneId, new UpdateLignePrixRequest(0.0));

        assertThat(updated.ht()).isEqualByComparingTo("0.00");
        assertThat(updated.tva()).isEqualByComparingTo("0.00");
        assertThat(updated.ttc()).isEqualByComparingTo("0.00");
    }

    @Test
    void aLineFromAnotherOrderIsNotFound() {
        AchatResponse first = createAchat(new BigDecimal("1"), 10.00);
        AchatResponse second = createAchat(new BigDecimal("1"), 10.00);

        UUID foreignLigneId = second.lignes().getFirst().id();

        assertThatThrownBy(() -> achatService.updateLignePrix(
                first.id(), foreignLigneId, new UpdateLignePrixRequest(99.00)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Nothing downstream has happened yet at EN_COURS / LIVRE, so no warning. */
    @Test
    void noWarningBeforeInvoicing() {
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.EN_COURS)).isNull();
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.LIVRE)).isNull();
    }

    /** Invoiced or paid, re-pricing leaves something out of step — say so. */
    @Test
    void warningOnceInvoicedOrPaid() {
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.FACTURE))
                .isNotNull()
                .contains("facture");
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.PAYE))
                .isNotNull()
                .contains("caisse");
    }

    /**
     * The point of moving prices to DOUBLE PRECISION: a unit price may now carry
     * more than two decimals, and it survives the round-trip instead of being
     * truncated to centimes on the way into the database.
     */
    @Test
    void aUnitPriceKeepsMoreThanTwoDecimals() {
        AchatResponse achat = createAchat(new BigDecimal("1000"), 0.1234);

        assertThat(achat.lignes().getFirst().prixUnitaire()).isEqualTo(0.1234);
        assertThat(achatService.findById(achat.id()).lignes().getFirst().prixUnitaire())
                .isEqualTo(0.1234);
    }

    /**
     * The extra precision is used in the calculation, but the line total is
     * still rounded to two decimals — that is the figure that gets invoiced.
     */
    @Test
    void theLineTotalIsStillRoundedToCentimes() {
        // 3 × 10.005 = 30.015 → 30.02 HALF_UP
        AchatResponse achat = createAchat(new BigDecimal("3"), 10.005);

        assertThat(achat.lignes().getFirst().total()).isEqualByComparingTo("30.02");
        assertThat(achat.ht()).isEqualByComparingTo("30.02");
        assertThat(achat.ttc()).isEqualByComparingTo("36.02");
    }

    /** Re-pricing accepts sub-centime values too. */
    @Test
    void repricingAcceptsMoreThanTwoDecimals() {
        AchatResponse achat = createAchat(new BigDecimal("200"), 1.00);
        UUID ligneId = achat.lignes().getFirst().id();

        AchatResponse updated = achatService.updateLignePrix(
                achat.id(), ligneId, new UpdateLignePrixRequest(0.3333));

        assertThat(updated.lignes().getFirst().prixUnitaire()).isEqualTo(0.3333);
        // 200 × 0.3333 = 66.66
        assertThat(updated.ht()).isEqualByComparingTo("66.66");
    }

    /** The ref is server-assigned now, and follows the ACH-<year>-NNN shape. */
    @Test
    void createdOrderGetsAGeneratedRef() {
        AchatResponse achat = createAchat(new BigDecimal("1"), 1.00);
        assertThat(achat.ref()).matches("^ACH-\\d{4}-\\d{3,}$");
    }

    // ── Fixtures ──────────────────────────────────────────────────────

    private AchatResponse createAchat(BigDecimal quantite, double prixUnitaire) {
        String suffix = "IT-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        UUID chantierId = chantierService.create(new CreateChantierRequest(
                "Chantier prix " + suffix, "Client", "adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(3),
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
                    c.setCode("ITP" + (SEQ.get() % 1000));
                    c.setLibelle("Catégorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-P-" + suffix);
        article.setDesignation("Article de test");
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(prixUnitaire);
        article.setTvaRate(new BigDecimal("20.00"));
        article = articleRepository.save(article);

        return achatService.create(new CreateAchatRequest(
                fournisseur.getId(), chantierId, LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(article.getId(), quantite, prixUnitaire, null)),
                null, null));
    }
}
