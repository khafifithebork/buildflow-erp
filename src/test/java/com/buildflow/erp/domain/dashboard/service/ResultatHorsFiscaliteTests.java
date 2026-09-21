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
 * Sur quoi se calcule le Résultat Hors Fiscalité.
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
     * La seule chose qui le sépare de la marge nette, désormais : les stocks.
     *
     * <p>C'est l'identité qui fige la formule. Elle ne tient que si les deux
     * indicateurs lisent les mêmes décaissements ; sous l'ancienne base elle
     * tombait dès que le périmètre effet chantier différait du total.
     *
     * <p>Passer par cette identité plutôt que par
     * {@code encaissements − décaissements} en direct : le DTO n'expose que
     * les encaissements TTC, la version HT reste interne au calcul.
     */
    @Test
    void stockIsTheOnlyDifferenceWithTheAccountingMargin() {
        DashboardKpisResponse k = dashboardService.getKpis(null);

        assertThat(k.margeNetteComptableHt().subtract(k.resultatHorsFiscaliteHt()))
                .isEqualByComparingTo(k.valeurStocksGlobaleHt());
    }

    /**
     * Le test qui distingue vraiment les deux bases.
     *
     * <p>Un achat réglé mais non marqué effet chantier sort du périmètre
     * « effet chantier » et entre dans les décaissements réels. L'indicateur
     * doit donc bouger de son montant HT — sous l'ancienne formule il n'aurait
     * pas bougé du tout.
     */
    @Test
    void aSettledOrderOutsideTheChantierScopeStillCounts() {
        DashboardKpisResponse avant = dashboardService.getKpis(null);

        AchatResponse achat = newAchat(false);
        payer(achat);

        DashboardKpisResponse apres = dashboardService.getKpis(null);

        assertThat(apres.resultatHorsFiscaliteHt())
                .isEqualByComparingTo(avant.resultatHorsFiscaliteHt().subtract(achat.ht()));

        // Et la preuve que les deux bases divergent bien sur ce cas :
        assertThat(apres.decaissementsEffetChantierHt())
                .isEqualByComparingTo(avant.decaissementsEffetChantierHt());
    }

    /** Un achat marqué effet chantier compte lui aussi : rien n'est exclu. */
    @Test
    void aSettledOrderInsideTheChantierScopeCountsToo() {
        DashboardKpisResponse avant = dashboardService.getKpis(null);

        AchatResponse achat = newAchat(true);
        payer(achat);

        assertThat(dashboardService.getKpis(null).resultatHorsFiscaliteHt())
                .isEqualByComparingTo(avant.resultatHorsFiscaliteHt().subtract(achat.ht()));
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
