package com.buildflow.erp.domain.dashboard.service;

import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.service.AchatService;
import com.buildflow.erp.domain.dashboard.dto.response.DashboardKpisResponse;
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

/**
 * Sur quoi se calculent les deux marges.
 *
 * <p>Il se lisait sur les seules opérations marquées effet chantier et non
 * effet fiscal. Le client a tranché autrement : ce qu'il veut voir, c'est tout
 * ce qui est réellement sorti — achats en HT, paie et caisse à leur montant,
 * soit exactement les décaissements réels de la marge nette.
 *
 * <p>Les deux indicateurs ne diffèrent donc plus que par les stocks, et c'est
 * ce que ces tests figent.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResultatHorsFiscaliteTests {

    @Autowired DashboardService dashboardService;
    @Autowired FournisseurService fournisseurService;
    @Autowired ChantierService chantierService;
    @Autowired AchatService achatService;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;

    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * Ce qui separe les deux marges, maintenant : deux choses a la fois.
     *
     * <p>Le perimetre des decaissements — le calcul 1 ecarte l'effet fiscal,
     * le calcul 2 non — et le perimetre du stock. L'ecart total vaut donc la
     * somme des deux ecarts, quel que soit le jeu de donnees.
     */
    @Test
    void theTwoMarginsDifferByDisbursementScopeAndStockScope() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        BigDecimal ecartDecaissements = k.decaissementsGlobauxHt().subtract(k.decaissementsReelsHt());
        BigDecimal ecartStock = k.valeurStocksEffetChantierHt().subtract(k.valeurStocksGlobaleHt());

        assertThat(k.margeNetteComptableHt().subtract(k.resultatHorsFiscaliteHt()))
                .isEqualByComparingTo(ecartDecaissements.add(ecartStock));
    }

    /** Le calcul 1 ecarte bien l'effet fiscal : son perimetre est plus etroit. */
    @Test
    void theRealReadingNeverExceedsTheGlobalOne() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.decaissementsReelsHt()).isLessThanOrEqualTo(k.decaissementsGlobauxHt());
    }

    /**
     * Le stock de l'effet chantier vaut encore le stock global.
     *
     * <p>Ce test fige une limite connue, pas une regle metier : rien ne permet
     * de rattacher un mouvement de stock a l'achat qui l'a cree. Il tombera le
     * jour ou ce lien existera — et ce jour-la il faudra le remplacer, pas le
     * reparer.
     */
    @Test
    void chantierStockStillEqualsGlobalStockForNow() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.valeurStocksEffetChantierHt()).isEqualByComparingTo(k.valeurStocksGlobaleHt());
    }

    /** Les deux calculs portent bien chacun un terme de stock. */
    @Test
    void bothReadingsCarryAStockTerm() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.resultatHorsFiscaliteHt())
                .isEqualByComparingTo(k.margeNetteComptableHt()
                        .add(k.decaissementsReelsHt()).subtract(k.decaissementsGlobauxHt())
                        .subtract(k.valeurStocksEffetChantierHt()).add(k.valeurStocksGlobaleHt()));
    }

    /**
     * Regler une commande livree ne bouge aucune des deux marges.
     *
     * <p>Et c'est juste : la commande sort 300 HT de tresorerie et fait entrer
     * 300 de stock. Les decaissements montent de 300, le stock monte de 300,
     * la marge ne bouge pas. Elle ne bougera qu'a la consommation du stock, ou
     * sur une depense sans contrepartie en stock.
     *
     * <p>C'est la consequence directe du terme de stock ajoute au calcul 2 :
     * avant, cet indicateur chutait du montant entier de chaque achat paye.
     */
    @Test
    void payingForDeliveredStockLeavesBothMarginsUnchanged() {
        DashboardKpisResponse avant = dashboardService.getKpis(null);

        AchatResponse achat = newAchat(false);
        payer(achat);

        DashboardKpisResponse apres = dashboardService.getKpis(null);

        // Les decaissements ont bien monte du montant HT...
        assertThat(apres.decaissementsGlobauxHt())
                .isEqualByComparingTo(avant.decaissementsGlobauxHt().add(achat.ht()));
        // ...et le stock aussi, donc les deux marges restent ou elles etaient.
        assertThat(apres.valeurStocksGlobaleHt())
                .isEqualByComparingTo(avant.valeurStocksGlobaleHt().add(achat.ht()));
        // Le calcul 2 compte la sortie et l'entree en stock : il ne bouge pas.
        assertThat(apres.resultatHorsFiscaliteHt())
                .isEqualByComparingTo(avant.resultatHorsFiscaliteHt());
        // Le calcul 1 ecarte cette sortie — non marquee effet chantier — mais
        // compte le stock entre. Il monte donc du montant HT de la commande.
        assertThat(apres.margeNetteComptableHt())
                .isEqualByComparingTo(avant.margeNetteComptableHt().add(achat.ht()));
    }

    /**
     * Un achat hors perimetre chantier compte quand meme dans les
     * decaissements globaux — c'est ce qui separe les deux agregats.
     */
    @Test
    void aSettledOrderOutsideTheChantierScopeStillCounts() {
        DashboardKpisResponse avant = dashboardService.getKpis(null);

        AchatResponse achat = newAchat(false);
        payer(achat);

        DashboardKpisResponse apres = dashboardService.getKpis(null);

        assertThat(apres.decaissementsGlobauxHt())
                .isEqualByComparingTo(avant.decaissementsGlobauxHt().add(achat.ht()));
        // Non marque effet chantier : l'agregat restreint ne bouge pas.
        assertThat(apres.decaissementsEffetChantierHt())
                .isEqualByComparingTo(avant.decaissementsEffetChantierHt());
    }

    /** Un achat marque effet chantier entre dans les deux agregats. */
    @Test
    void aSettledOrderInsideTheChantierScopeCountsToo() {
        DashboardKpisResponse avant = dashboardService.getKpis(null);

        AchatResponse achat = newAchat(true);
        payer(achat);

        DashboardKpisResponse apres = dashboardService.getKpis(null);

        assertThat(apres.decaissementsGlobauxHt())
                .isEqualByComparingTo(avant.decaissementsGlobauxHt().add(achat.ht()));
        assertThat(apres.decaissementsEffetChantierHt())
                .isEqualByComparingTo(avant.decaissementsEffetChantierHt().add(achat.ht()));
    }

    /**
     * Les stocks par emplacement sont informatifs : ils n'entrent nulle part.
     *
     * <p>Le découpage par emplacement somme au même total que le découpage par
     * disponibilité — c'est le même stock, lu deux fois. Et la marge nette ne
     * lit que le global : si l'une des deux ventilations entrait dans une
     * formule, l'identité stocks de l'autre test tomberait.
     */
    @Test
    void theStockBreakdownsAreInformationalOnly() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.valeurStocksAuDepotHt().add(k.valeurStocksSurChantiersHt()))
                .isEqualByComparingTo(k.valeurStocksGlobaleHt());
        assertThat(k.valeurStocksDepotHt().add(k.valeurStocksEnTravauxHt()))
                .isEqualByComparingTo(k.valeurStocksGlobaleHt());
    }

    /** Une commande non réglée ne sort rien, donc ne bouge pas l'indicateur. */
    @Test
    void anUnsettledOrderDoesNotMoveIt() {
        DashboardKpisResponse avant = dashboardService.getKpis(null);

        newAchat(true);

        assertThat(dashboardService.getKpis(null).resultatHorsFiscaliteHt())
                .isEqualByComparingTo(avant.resultatHorsFiscaliteHt());
    }

    // -- Fixtures ---------------------------------------------------

    private AchatResponse newAchat(boolean effetChantier) {
        String suffix = "RHF-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        UUID fournisseurId = fournisseurService.create(new CreateFournisseurRequest(
                "Fournisseur " + suffix, "ICE" + suffix, "Contact", "0600000000",
                suffix + "@test.ma", "Casablanca", "Adresse", "RIB", "Banque",
                FournisseurStatut.ACTIF, List.of())).id();

        UUID chantierId = chantierService.create(new CreateChantierRequest(
                "Chantier " + suffix, "Client", "Adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(6),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();

        CategorieArticle categorie = categorieArticleRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategorieArticle c = new CategorieArticle();
                    c.setCode("RHF" + (SEQ.get() % 1000));
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
        article = articleRepository.save(article);

        return achatService.create(new CreateAchatRequest(
                fournisseurId, chantierId, LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(article.getId(), 3.0, 100.0, null)),
                effetChantier, false));
    }

    /** Réglé par virement : reste hors caisse, donc compté une seule fois. */
    private void payer(AchatResponse achat) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        achatService.validateBL(achat.id(), "BL-" + suffix);
        achatService.validateFacture(achat.id(), "FA-" + suffix);
        achatService.validatePaiement(achat.id(), ModePaiement.VIREMENT);
    }
}
