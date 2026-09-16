package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.service.AchatService;
import com.buildflow.erp.domain.dashboard.service.DashboardService;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.dto.response.FournisseurResponse;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.entity.FournisseurStatut;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
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
 * Le solde impayé d'un fournisseur.
 *
 * <p>{@code fournisseurs.solde_impaye} existe en base depuis la migration 004
 * et n'a jamais eu d'écrivain : la page Fournisseurs et l'export Excel
 * affichaient zéro quelle que soit la dette réelle. La valeur se dérive
 * désormais des achats non soldés.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FournisseurSoldeImpayeTests {

    @Autowired FournisseurService fournisseurService;
    @Autowired ChantierService chantierService;
    @Autowired AchatService achatService;
    @Autowired DashboardService dashboardService;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Test
    void aSupplierWithNoOrdersOwesNothing() {
        UUID id = newFournisseur();

        assertThat(fournisseurService.findById(id).soldeImpaye()).isEqualByComparingTo("0.00");
    }

    /** Le cas qui a motivé le correctif : la dette existait, l'écran affichait 0. */
    @Test
    void anUnpaidOrderShowsUpAsDebt() {
        UUID id = newFournisseur();
        AchatResponse achat = newAchat(id, 10.0, 100.0);

        assertThat(fournisseurService.findById(id).soldeImpaye())
                .isEqualByComparingTo(achat.ttc());
    }

    @Test
    void severalUnpaidOrdersAddUp() {
        UUID id = newFournisseur();
        BigDecimal a = newAchat(id, 2.0, 50.0).ttc();
        BigDecimal b = newAchat(id, 3.0, 20.0).ttc();

        assertThat(fournisseurService.findById(id).soldeImpaye())
                .isEqualByComparingTo(a.add(b));
    }

    /** Une commande soldée sort de la dette : c'est la définition même. */
    @Test
    void payingAnOrderClearsItFromTheBalance() {
        UUID id = newFournisseur();
        AchatResponse achat = newAchat(id, 4.0, 25.0);
        payer(achat);

        assertThat(fournisseurService.findById(id).soldeImpaye()).isEqualByComparingTo("0.00");
    }

    @Test
    void onlyTheUnpaidPartRemains() {
        UUID id = newFournisseur();
        AchatResponse paid = newAchat(id, 1.0, 300.0);
        BigDecimal open = newAchat(id, 1.0, 120.0).ttc();
        payer(paid);

        assertThat(fournisseurService.findById(id).soldeImpaye()).isEqualByComparingTo(open);
    }

    /** La liste passe par une requête groupée ; elle doit dire la même chose. */
    @Test
    void theListAgreesWithTheSingleLookup() {
        UUID id = newFournisseur();
        newAchat(id, 5.0, 40.0);

        BigDecimal fromList = fournisseurService.findAll().stream()
                .filter(f -> f.id().equals(id))
                .map(FournisseurResponse::soldeImpaye)
                .findFirst()
                .orElseThrow();

        assertThat(fromList).isEqualByComparingTo(fournisseurService.findById(id).soldeImpaye());
    }

    @Test
    void aSupplierWithoutOrdersStillAppearsInTheListAtZero() {
        UUID id = newFournisseur();

        assertThat(fournisseurService.findAll())
                .filteredOn(f -> f.id().equals(id))
                .singleElement()
                .extracting(FournisseurResponse::soldeImpaye)
                .satisfies(v -> assertThat((BigDecimal) v).isEqualByComparingTo("0.00"));
    }

    /**
     * L'invariant qui compte : les soldes par fournisseur somment au total
     * affiché sur la carte Dettes Fournisseurs du tableau de bord. Les deux
     * chiffres viennent de la même définition, ils ne peuvent pas diverger.
     */
    @Test
    void perSupplierBalancesSumToTheDashboardTotal() {
        UUID id = newFournisseur();
        newAchat(id, 3.0, 70.0);

        BigDecimal somme = fournisseurService.findAll().stream()
                .map(FournisseurResponse::soldeImpaye)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(somme).isEqualByComparingTo(dashboardService.getKpis(null).dettesFournisseursTtc());
    }

    // -- Fixtures ---------------------------------------------------

    private UUID newFournisseur() {
        String suffix = "SI-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        return fournisseurService.create(new CreateFournisseurRequest(
                "Fournisseur " + suffix, "ICE" + suffix, "Contact", "0600000000",
                suffix + "@test.ma", "Casablanca", "Adresse", "RIB", "Banque",
                FournisseurStatut.ACTIF, List.of())).id();
    }

    private AchatResponse newAchat(UUID fournisseurId, double qte, double pu) {
        String suffix = "SI-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        UUID chantierId = chantierService.create(new CreateChantierRequest(
                "Chantier " + suffix, "Client", "Adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(6),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();

        CategorieArticle categorie = categorieArticleRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategorieArticle c = new CategorieArticle();
                    c.setCode("SIC" + (SEQ.get() % 1000));
                    c.setLibelle("Catégorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-" + suffix);
        article.setDesignation("Article de test");
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(pu);
        article.setTvaRate(new BigDecimal("20.00"));
        article = articleRepository.save(article);

        return achatService.create(new CreateAchatRequest(
                fournisseurId, chantierId, LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(article.getId(), qte, pu, null)),
                null, null));
    }

    private void payer(AchatResponse achat) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        achatService.validateBL(achat.id(), "BL-" + suffix);
        achatService.validateFacture(achat.id(), "FA-" + suffix);
        achatService.validatePaiement(achat.id(), ModePaiement.VIREMENT);
    }
}
