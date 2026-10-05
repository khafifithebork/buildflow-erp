package com.buildflow.erp.domain.stock.service;

import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.service.AchatService;
import com.buildflow.erp.domain.dashboard.dto.response.DashboardKpisResponse;
import com.buildflow.erp.domain.dashboard.service.DashboardService;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.entity.FournisseurStatut;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
import com.buildflow.erp.domain.referentiel.service.FournisseurService;
import com.buildflow.erp.domain.stock.repository.StockArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Quel stock relève de l'effet chantier.
 *
 * <p>Le stock est fongible : il se valorise en moyenne pondérée par ligne, donc
 * aucune unité détenue ne porte d'origine. La part effet chantier ne se lit pas,
 * elle se convient — et la convention retenue est le <b>prorata en valeur des
 * entrées</b>.
 *
 * <p>Ce que ces tests figent, ce n'est pas une vérité comptable mais ce choix,
 * et surtout ce qui le distingue du prorata en quantité : à prix d'achat
 * différents, les deux ne donnent pas le même chiffre, et c'est le seul cas où
 * la convention se voit.
 *
 * <p>Chaque test travaille sur un article et un chantier neufs, donc sur une
 * ligne de stock à lui. Les écarts mesurés sont les siens — rien de ce que la
 * base contient par ailleurs n'entre dans le calcul.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockEffetChantierTests {

    @Autowired StockService stockService;
    @Autowired DashboardService dashboardService;
    @Autowired AchatService achatService;
    @Autowired FournisseurService fournisseurService;
    @Autowired ChantierService chantierService;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;
    @Autowired StockArticleRepository stockArticleRepository;

    private UUID fournisseurId;
    private UUID chantierId;
    private UUID articleId;

    /** Un sou près : les prix sont des doubles jusqu'à l'arrondi final. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    // -- La convention ----------------------------------------------

    /** Une commande à effet chantier livrée entre en entier dans le périmètre. */
    @Test
    void aChantierPurchaseLandsEntirelyInTheChantierStock() {
        BigDecimal avant = stockService.valeurStockEffetChantierHt();

        livrer(10.0, 100.0, true, false);

        assertThat(stockService.valeurStockEffetChantierHt().subtract(avant))
                .isCloseTo(new BigDecimal("1000"), within(TOLERANCE));
    }

    /**
     * Une commande sans effet chantier alimente le stock global et lui seul.
     *
     * <p>C'est le cœur de l'affaire : avant, cette commande montait aussi dans
     * le calcul 1, qui comptait le stock global faute de savoir le découper.
     */
    @Test
    void aPurchaseOutsideTheChantierScopeLandsNowhere() {
        BigDecimal avantEc = stockService.valeurStockEffetChantierHt();
        BigDecimal avantGlobal = stockGlobal();

        livrer(10.0, 100.0, false, false);

        assertThat(stockGlobal().subtract(avantGlobal))
                .isCloseTo(new BigDecimal("1000"), within(TOLERANCE));
        assertThat(stockService.valeurStockEffetChantierHt())
                .isCloseTo(avantEc, within(TOLERANCE));
    }

    /**
     * Une commande à effet fiscal non plus — le périmètre est
     * {@code effet chantier ET NON effet fiscal}, exactement celui des
     * décaissements réels.
     */
    @Test
    void aFiscalPurchaseLandsNowhereEither() {
        BigDecimal avantEc = stockService.valeurStockEffetChantierHt();
        BigDecimal avantGlobal = stockGlobal();

        livrer(10.0, 100.0, true, true);

        assertThat(stockGlobal().subtract(avantGlobal))
                .isCloseTo(new BigDecimal("1000"), within(TOLERANCE));
        assertThat(stockService.valeurStockEffetChantierHt())
                .isCloseTo(avantEc, within(TOLERANCE));
    }

    /**
     * Le test qui tranche entre les deux prorata possibles.
     *
     * <p>Même article, même chantier, donc une seule ligne de stock, alimentée
     * par deux commandes à des prix différents :
     *
     * <pre>
     *   10 unités à 100, effet chantier      → 1 000
     *   10 unités à 300, hors périmètre      → 3 000
     *   ───────────────────────────────────────────────
     *   20 unités, coût moyen 200            → 4 000
     * </pre>
     *
     * <p>En valeur, la part effet chantier est de 1 000 — le quart. En quantité
     * elle serait de 2 000 — la moitié, puisque 10 unités sur 20 viennent de la
     * première commande. Les deux conventions divergent ici, et c'est la
     * première qui est retenue : la seconde attribuerait au périmètre chantier
     * 1 000 de marchandise qu'il n'a jamais payée.
     */
    @Test
    void aMixedLineSplitsByValueNotByQuantity() {
        BigDecimal avant = stockService.valeurStockEffetChantierHt();

        livrer(10.0, 100.0, true, false);
        livrer(10.0, 300.0, false, false);

        BigDecimal part = stockService.valeurStockEffetChantierHt().subtract(avant);

        assertThat(part).isCloseTo(new BigDecimal("1000"), within(TOLERANCE));
        // Explicite, parce que c'est l'erreur que ce test existe pour exclure.
        assertThat(part).isNotCloseTo(new BigDecimal("2000"), within(TOLERANCE));
    }

    /**
     * L'inverse du test précédent : à prix égal, les deux prorata coïncident et
     * la convention ne se voit pas. La moitié des unités fait la moitié de la
     * valeur.
     */
    @Test
    void aMixedLineAtEqualPricesSplitsInHalf() {
        BigDecimal avant = stockService.valeurStockEffetChantierHt();

        livrer(10.0, 100.0, true, false);
        livrer(10.0, 100.0, false, false);

        assertThat(stockService.valeurStockEffetChantierHt().subtract(avant))
                .isCloseTo(new BigDecimal("1000"), within(TOLERANCE));
    }

    /**
     * Quand toutes les commandes partagent leurs indicateurs, les deux lectures
     * du stock donnent le même chiffre.
     *
     * <p>C'est le cas dégradé, et probablement le cas courant : le découpage ne
     * sert qu'à un parc d'achats réellement mélangé.
     */
    @Test
    void theTwoReadingsAgreeWhenEveryOrderSharesItsFlags() {
        BigDecimal avantEc = stockService.valeurStockEffetChantierHt();
        BigDecimal avantGlobal = stockGlobal();

        livrer(10.0, 100.0, true, false);
        livrer(5.0, 100.0, true, false);

        assertThat(stockService.valeurStockEffetChantierHt().subtract(avantEc))
                .isCloseTo(stockGlobal().subtract(avantGlobal), within(TOLERANCE));
    }

    // -- Les invariants ---------------------------------------------

    /**
     * Le stock de l'effet chantier ne dépasse jamais le stock global.
     *
     * <p>Vrai par construction — le numérateur du prorata est un sous-ensemble
     * de son dénominateur, donc la part de chaque ligne vaut au plus sa valeur.
     * Figé quand même : c'est l'invariant qui tomberait le premier si quelqu'un
     * inversait le sens d'un des deux indicateurs dans la requête.
     */
    @Test
    void theChantierStockNeverExceedsTheGlobalStock() {
        livrer(10.0, 100.0, true, false);
        livrer(10.0, 300.0, false, true);

        assertThat(stockService.valeurStockEffetChantierHt())
                .isLessThanOrEqualTo(stockGlobal().add(TOLERANCE));
    }

    /**
     * Les deux stocks divergent, là où ils étaient égaux par construction.
     *
     * <p>Remplace le test qui figeait leur égalité en attendant ce lien. Il
     * disait qu'il faudrait le remplacer et non le réparer, et c'est ce qui se
     * passe ici.
     */
    @Test
    void theTwoStocksDivergeOnceAFiscalPurchaseFeedsTheStock() {
        livrer(10.0, 100.0, true, true);

        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.valeurStocksEffetChantierHt()).isLessThan(k.valeurStocksGlobaleHt());
    }

    /**
     * Une entrée saisie à la main dilue la part effet chantier, elle ne s'y
     * ajoute pas.
     *
     * <p>Son prix n'est enregistré nulle part et aucune commande ne la porte :
     * elle entre au dénominateur au coût courant de la ligne, jamais au
     * numérateur. 10 unités achetées en effet chantier à 100 puis 10 saisies à
     * la main laissent une ligne de 2 000 dont la moitié seulement est
     * attribuée.
     *
     * <p>Le choix est de ne rien affirmer sur une origine inconnue. L'autre
     * option — suivre l'indicateur de la ligne existante — attribuerait au
     * périmètre chantier du stock dont personne ne sait d'où il vient.
     */
    @Test
    void aHandEnteredArrivalDilutesTheShareRatherThanJoiningIt() {
        BigDecimal avant = stockService.valeurStockEffetChantierHt();

        livrer(10.0, 100.0, true, false);
        stockService.createMouvement(new com.buildflow.erp.domain.stock.dto.request
                .CreateMouvementStockRequest(
                articleId, chantierId,
                com.buildflow.erp.domain.stock.entity.TypeMouvement.ENTREE,
                new BigDecimal("10.000"), "Saisie manuelle", null));

        // La ligne vaut 2 000 et la part reste 1 000 : la moitié.
        assertThat(stockService.valeurStockEffetChantierHt().subtract(avant))
                .isCloseTo(new BigDecimal("1000"), within(TOLERANCE));
    }

    /**
     * Affecter aux travaux ne déplace pas la part : c'est la même marchandise,
     * lue dans l'autre colonne.
     */
    @Test
    void incorporatingIntoTheWorksLeavesTheShareAlone() {
        livrer(10.0, 100.0, true, false);
        BigDecimal avant = stockService.valeurStockEffetChantierHt();

        stockService.affecterAuxTravaux(new com.buildflow.erp.domain.stock.dto.request
                .AffecterTravauxRequest(articleId, chantierId, new BigDecimal("4.000"), "Pose"));

        assertThat(stockService.valeurStockEffetChantierHt())
                .isCloseTo(avant, within(TOLERANCE));
    }

    // -- Fixtures ---------------------------------------------------

    /**
     * Un fournisseur, un chantier et un article neufs par test, donc une ligne
     * de stock isolée : les écarts mesurés ne doivent rien au reste de la base.
     */
    @BeforeEach
    void setUp() {
        String suffix = "SEC-" + UUID.randomUUID().toString().substring(0, 8);

        fournisseurId = fournisseurService.create(new CreateFournisseurRequest(
                "Fournisseur " + suffix, "ICE" + suffix, "Contact", "0600000000",
                suffix + "@test.ma", "Casablanca", "Adresse", "RIB", "Banque",
                FournisseurStatut.ACTIF, List.of())).id();

        chantierId = chantierService.create(new CreateChantierRequest(
                "Chantier " + suffix, "Client", "Adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(6),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();

        CategorieArticle categorie = categorieArticleRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategorieArticle c = new CategorieArticle();
                    c.setCode("SEC");
                    c.setLibelle("Catégorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-" + suffix);
        article.setDesignation("Article de test");
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(100.0);
        article.setTvaRate(new BigDecimal("20.00"));
        articleId = articleRepository.save(article).getId();
    }

    /**
     * Commande livrée, donc entrée en stock. Le règlement n'entre pas en jeu :
     * le stock se valorise à la réception, pas au paiement.
     */
    private AchatResponse livrer(double quantite, double prixUnitaire,
                                 boolean effetChantier, boolean effetFiscal) {
        AchatResponse achat = achatService.create(new CreateAchatRequest(
                fournisseurId, chantierId, LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(articleId, quantite, prixUnitaire, null)),
                effetChantier, effetFiscal));

        achatService.validateBL(achat.id(), "BL-" + UUID.randomUUID().toString().substring(0, 8));
        return achat;
    }

    private BigDecimal stockGlobal() {
        return BigDecimal.valueOf(stockArticleRepository.sumValeurStockHt());
    }
}
