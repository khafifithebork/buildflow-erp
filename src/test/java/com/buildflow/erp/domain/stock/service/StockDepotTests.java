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
     * The point of the whole change: the two dashboard figures are computed,
     * they add up to the global total, and a transfer shifts value between them
     * without changing that total.
     */
    @Test
    void theDashboardSplitAddsUpAndFollowsATransfer() {
        UUID articleId = newArticle();
        UUID chantierId = newChantier();

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.ENTREE, new BigDecimal("100"), null, null));

        var before = dashboardService.getKpis(null);
        assertThat(before.valeurStocksDepotHt().add(before.valeurStocksEnTravauxHt()))
                .isEqualByComparingTo(before.valeurStocksGlobaleHt());

        stockService.createMouvement(new CreateMouvementStockRequest(
                articleId, null, TypeMouvement.TRANSFERT, new BigDecimal("40"), null, chantierId));

        var after = dashboardService.getKpis(null);

        assertThat(after.valeurStocksGlobaleHt()).isEqualByComparingTo(before.valeurStocksGlobaleHt());
        assertThat(after.valeurStocksDepotHt())
                .isEqualByComparingTo(before.valeurStocksDepotHt().subtract(new BigDecimal("4000.00")));
        assertThat(after.valeurStocksEnTravauxHt())
                .isEqualByComparingTo(before.valeurStocksEnTravauxHt().add(new BigDecimal("4000.00")));
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
