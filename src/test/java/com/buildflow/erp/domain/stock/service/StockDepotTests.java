package com.buildflow.erp.domain.stock.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.domain.dashboard.service.DashboardService;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
import com.buildflow.erp.domain.stock.dto.request.AffecterTravauxRequest;
import com.buildflow.erp.domain.stock.dto.request.CreateMouvementStockRequest;
import com.buildflow.erp.domain.stock.dto.response.StockArticleResponse;
import com.buildflow.erp.domain.stock.entity.TypeMouvement;
import com.buildflow.erp.domain.stock.repository.StockArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
 * Stock now has a location: the central dépôt or a chantier.
 *
 * <p>This is what makes the dashboard's Dépôts / En Travaux split real — it was
 * hardcoded to 0 / 0 because nothing in the model could distinguish the two.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockDepotTests {

    @Autowired StockService stockService;
    @Autowired StockArticleRepository stockArticleRepository;
    @Autowired ChantierService chantierService;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;
    @Autowired DashboardService dashboardService;

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** An ENTREE with no chantier receives goods into the warehouse. */
    @Test
    void stockCanBeReceivedIntoTheDepot() {
        UUID articleId = newArticle();

        StockArticleResponse stock = stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("100"), "RECEPTION", null));

        assertThat(stock.emplacement()).isEqualTo("DEPOT");
        assertThat(stock.chantierId()).isNull();
        assertThat(stock.chantierNom()).isEqualTo("Dépôt central");
        assertThat(stock.quantiteTheorique()).isEqualByComparingTo("100");
    }

    /** The transfer that used to be rejected outright now moves the quantity. */
    @Test
    void stockTransfersFromTheDepotToAChantier() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("100"), null, null));

        StockArticleResponse arrived = stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.TRANSFERT, new BigDecimal("30"), null, chantierId));

        assertThat(arrived.emplacement()).isEqualTo("CHANTIER");
        assertThat(arrived.quantiteTheorique()).isEqualByComparingTo("30");

        assertThat(stockArticleRepository.findByArticleIdAndChantierIsNull(articleId)
                .orElseThrow().getQuantiteTheorique()).isEqualByComparingTo("70");
    }

    /** And back again, so material returned from a site re-enters the warehouse. */
    @Test
    void stockTransfersFromAChantierBackToTheDepot() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.ENTREE, new BigDecimal("50"), null, null));

        StockArticleResponse returned = stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.TRANSFERT, new BigDecimal("20"), null, null));

        assertThat(returned.emplacement()).isEqualTo("DEPOT");
        assertThat(returned.quantiteTheorique()).isEqualByComparingTo("20");
    }

    @Test
    void transferringToTheSameLocationIsRefused() {
        UUID articleId = newArticle();
        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("10"), null, null));

        assertThatThrownBy(() -> stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.TRANSFERT, new BigDecimal("5"), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("identiques");
    }

    @Test
    void transferringMoreThanIsHeldIsRefused() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();
        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("10"), null, null));

        assertThatThrownBy(() -> stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.TRANSFERT, new BigDecimal("999"), null, chantierId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("insuffisante");
    }

    /** The dépôt holds one line per article, not one per movement. */
    @Test
    void repeatedReceiptsAccumulateOnOneDepotLine() {
        UUID articleId = newArticle();

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("10"), null, null));
        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("15"), null, null));

        assertThat(stockService.getStockDepot(PageRequest.of(0, 50)).content())
                .filteredOn(s -> s.quantiteTheorique().compareTo(new BigDecimal("25")) == 0)
                .hasSize(1);
    }

    /**
     * The dashboard split is by AVAILABILITY, not by location: Dépôts is what is
     * still in stock, En Travaux is what has been posé. Moving material between
     * a warehouse and a site changes neither — it is available in both places.
     */
    @Test
    void aLocationTransferDoesNotMoveTheDashboardSplit() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("100"), null, null));

        var before = dashboardService.getKpis(null);
        assertThat(before.valeurStocksDepotHt().add(before.valeurStocksEnTravauxHt()))
                .as("the split always sums to the total")
                .isEqualByComparingTo(before.valeurStocksGlobaleHt());

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.TRANSFERT, new BigDecimal("40"), null, chantierId));

        var after = dashboardService.getKpis(null);
        assertThat(after.valeurStocksGlobaleHt()).isEqualByComparingTo(before.valeurStocksGlobaleHt());
        assertThat(after.valeurStocksDepotHt()).isEqualByComparingTo(before.valeurStocksDepotHt());
        assertThat(after.valeurStocksEnTravauxHt()).isEqualByComparingTo(before.valeurStocksEnTravauxHt());
    }

    /**
     * Affecting to the works is what moves the split: quantity goes from
     * available to posé at the same location, so value shifts from Dépôts to
     * En Travaux while the total stays put.
     */
    @Test
    void affectingToTheWorksMovesTheSplitWithoutChangingTheTotal() {
        UUID articleId = newArticle();   // 100 MAD each

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("100"), null, null));

        var before = dashboardService.getKpis(null);

        StockArticleResponse after = stockService.affecterAuxTravaux(
                new AffecterTravauxRequest(articleId, null, new BigDecimal("30"), "Pose"));

        assertThat(after.quantiteTheorique()).isEqualByComparingTo("70");
        assertThat(after.quantiteTravaux()).isEqualByComparingTo("30");

        var k = dashboardService.getKpis(null);
        assertThat(k.valeurStocksGlobaleHt())
                .as("total value is unchanged — nothing was consumed in value terms")
                .isEqualByComparingTo(before.valeurStocksGlobaleHt());
        assertThat(k.valeurStocksDepotHt())
                .isEqualByComparingTo(before.valeurStocksDepotHt().subtract(new BigDecimal("3000.00")));
        assertThat(k.valeurStocksEnTravauxHt())
                .isEqualByComparingTo(before.valeurStocksEnTravauxHt().add(new BigDecimal("3000.00")));
        assertThat(k.valeurStocksDepotHt().add(k.valeurStocksEnTravauxHt()))
                .isEqualByComparingTo(k.valeurStocksGlobaleHt());
    }

    /** Material already posé cannot be posé again. */
    @Test
    void affectingMoreThanIsAvailableIsRefused() {
        UUID articleId = newArticle();
        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("10"), null, null));

        stockService.affecterAuxTravaux(
                new AffecterTravauxRequest(articleId, null, new BigDecimal("10"), null));

        assertThatThrownBy(() -> stockService.affecterAuxTravaux(
                new AffecterTravauxRequest(articleId, null, new BigDecimal("1"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("insuffisante");
    }

    /** Affecting works on a chantier's stock too, not just the dépôt. */
    @Test
    void affectingWorksAtAChantierLocation() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.ENTREE, new BigDecimal("50"), null, null));

        StockArticleResponse after = stockService.affecterAuxTravaux(
                new AffecterTravauxRequest(articleId, chantierId, new BigDecimal("20"), null));

        assertThat(after.emplacement()).isEqualTo("CHANTIER");
        assertThat(after.quantiteTheorique()).isEqualByComparingTo("30");
        assertThat(after.quantiteTravaux()).isEqualByComparingTo("20");
    }

    // ── Écarts d'inventaire ───────────────────────────────────────────

    /**
     * Régression : la quantité était bornée au positif pour tous les types, si
     * bien qu'un ajustement ne pouvait que monter. Un manquant devait être saisi
     * en SORTIE, où il se mélangeait aux consommations réelles — la donnée
     * « ce qui a réellement été posé » en sortait fausse.
     */
    @Test
    void unAjustementConstateUnManquant() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();
        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.ENTREE, new BigDecimal("10"), "BL", null));

        // inventaire physique : 7 au lieu de 10, trois unités cassées
        StockArticleResponse apres = stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.AJUSTEMENT, new BigDecimal("-3"), "inventaire", null));

        assertThat(apres.quantiteTheorique()).isEqualByComparingTo("7");
    }

    /** Et un excédent, dans l'autre sens. */
    @Test
    void unAjustementConstateUnExcedent() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();
        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.ENTREE, new BigDecimal("10"), "BL", null));

        StockArticleResponse apres = stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.AJUSTEMENT, new BigDecimal("2"), "inventaire", null));

        assertThat(apres.quantiteTheorique()).isEqualByComparingTo("12");
    }

    /**
     * Un écart d'inventaire ne dit rien du prix payé : il constate une quantité.
     * La marchandise restante vaut ce qu'elle a coûté, ni plus ni moins.
     */
    @Test
    void unAjustementNeChangePasLeCoutUnitaire() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();
        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.ENTREE, new BigDecimal("10"), "BL", null));
        double coutAvant = ligne(articleId, chantierId).getCoutUnitaire();

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.AJUSTEMENT, new BigDecimal("-3"), "inventaire", null));

        assertThat(ligne(articleId, chantierId).getCoutUnitaire()).isEqualTo(coutAvant);
    }

    /** On ne peut pas constater un manquant plus grand que ce qui est en stock. */
    @Test
    void unManquantNePeutPasDepasserLeStock() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();
        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.ENTREE, new BigDecimal("10"), "BL", null));

        assertThatThrownBy(() -> stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.AJUSTEMENT, new BigDecimal("-40"), "inventaire", null)))
                .hasMessageContaining("insuffisante");
    }

    /** Une sortie négative ne veut rien dire : le sens est déjà dans le type. */
    @Test
    void seulUnAjustementPeutEtreNegatif() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();

        assertThatThrownBy(() -> stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.SORTIE, new BigDecimal("-5"), "?", null)))
                .hasMessageContaining("AJUSTEMENT");
    }

    /** Et un ajustement de zéro ne constate aucun écart. */
    @Test
    void unAjustementDeZeroEstRefuse() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();

        assertThatThrownBy(() -> stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, chantierId, TypeMouvement.AJUSTEMENT, BigDecimal.ZERO, "inventaire", null)))
                .hasMessageContaining("aucun écart");
    }

    private com.buildflow.erp.domain.stock.entity.StockArticle ligne(UUID articleId, UUID chantierId) {
        return stockArticleRepository.findByArticleIdAndChantierId(articleId, chantierId).orElseThrow();
    }

    // ── Fixtures ──────────────────────────────────────────────────────

    private UUID newArticle() {
        String suffix = "DEP-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        CategorieArticle categorie = categorieArticleRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategorieArticle c = new CategorieArticle();
                    c.setCode("DPC" + (SEQ.get() % 1000));
                    c.setLibelle("Catégorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-" + suffix);
        article.setDesignation("Article " + suffix);
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(100.0); // 100 MAD each, so quantities read as value x100
        article.setTvaRate(new BigDecimal("20.00"));
        return articleRepository.save(article).getId();
    }

    private UUID newChantier() {
        String suffix = "DEP-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        return chantierService.create(new CreateChantierRequest(
                "Chantier " + suffix, "Client", "Adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(6),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();
    }
}
