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

/** Both results exclude stock and differ only by the scope of disbursements. */
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

    @Test
    void theTwoMarginsDifferOnlyByDisbursementScope() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        BigDecimal ecartDecaissements = k.decaissementsGlobauxHt().subtract(k.decaissementsReelsHt());

        assertThat(k.resultatHorsFiscaliteHt().subtract(k.margeNetteComptableHt()))
                .isEqualByComparingTo(ecartDecaissements);
    }

    @Test
    void theRealReadingNeverExceedsTheGlobalOne() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.decaissementsReelsHt()).isLessThanOrEqualTo(k.decaissementsGlobauxHt());
    }

    @Test
    void chantierStockEqualsGlobalStockAfterRollback() {
        newAchat(false);
        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.valeurStocksEffetChantierHt()).isEqualByComparingTo(k.valeurStocksGlobaleHt());
    }

    @Test
    void deliveringUnpaidStockDoesNotChangeEitherResult() {
        AchatResponse achat = newAchat(true);
        DashboardKpisResponse before = dashboardService.getKpis(null);
        achatService.validateBL(achat.id(), "BL-" + UUID.randomUUID());
        DashboardKpisResponse after = dashboardService.getKpis(null);

        assertThat(after.valeurStocksGlobaleHt())
                .isEqualByComparingTo(before.valeurStocksGlobaleHt().add(achat.ht()));
        assertThat(after.margeNetteComptableHt()).isEqualByComparingTo(before.margeNetteComptableHt());
        assertThat(after.resultatHorsFiscaliteHt()).isEqualByComparingTo(before.resultatHorsFiscaliteHt());
    }

    @Test
    void payingForStockOutsideChantierScopeReducesOnlyGlobalResult() {
        DashboardKpisResponse avant = dashboardService.getKpis(null);

        AchatResponse achat = newAchat(false);
        payer(achat);

        DashboardKpisResponse apres = dashboardService.getKpis(null);

        assertThat(apres.decaissementsGlobauxHt())
                .isEqualByComparingTo(avant.decaissementsGlobauxHt().add(achat.ht()));
        assertThat(apres.valeurStocksGlobaleHt())
                .isEqualByComparingTo(avant.valeurStocksGlobaleHt().add(achat.ht()));
        assertThat(apres.margeNetteComptableHt())
                .isEqualByComparingTo(avant.margeNetteComptableHt().subtract(achat.ht()));
        assertThat(apres.resultatHorsFiscaliteHt())
                .isEqualByComparingTo(avant.resultatHorsFiscaliteHt());
    }

    @Test
    void aSettledOrderOutsideTheChantierScopeStillCounts() {
        DashboardKpisResponse avant = dashboardService.getKpis(null);

        AchatResponse achat = newAchat(false);
        payer(achat);

        DashboardKpisResponse apres = dashboardService.getKpis(null);

        assertThat(apres.decaissementsGlobauxHt())
                .isEqualByComparingTo(avant.decaissementsGlobauxHt().add(achat.ht()));
        assertThat(apres.decaissementsEffetChantierHt())
                .isEqualByComparingTo(avant.decaissementsEffetChantierHt());
    }

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
        assertThat(apres.margeNetteComptableHt())
                .isEqualByComparingTo(avant.margeNetteComptableHt().subtract(achat.ht()));
        assertThat(apres.resultatHorsFiscaliteHt())
                .isEqualByComparingTo(avant.resultatHorsFiscaliteHt().subtract(achat.ht()));
    }

    @Test
    void theStockBreakdownsAreInformationalOnly() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.valeurStocksAuDepotHt().add(k.valeurStocksSurChantiersHt()))
                .isEqualByComparingTo(k.valeurStocksGlobaleHt());
        assertThat(k.valeurStocksDepotHt().add(k.valeurStocksEnTravauxHt()))
                .isEqualByComparingTo(k.valeurStocksGlobaleHt());
    }

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

    /** Bank transfer: counted outside cash, once only. */
    private void payer(AchatResponse achat) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        achatService.validateBL(achat.id(), "BL-" + suffix);
        achatService.validateFacture(achat.id(), "FA-" + suffix);
        achatService.validatePaiement(achat.id(), ModePaiement.VIREMENT);
    }
}
