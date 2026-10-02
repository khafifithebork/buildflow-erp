package com.buildflow.erp.domain.achats.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.repository.PaiementAchatRepository;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.dto.response.FournisseurResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le reglement partiel d'une commande fournisseur.
 *
 * <p>Le cas que le client a decrit : une dette de 100 000 dont on regle 10 000,
 * les 10 000 basculant en « deja paye » et 90 000 restant dus. Jusqu'ici une
 * commande etait payee ou ne l'etait pas, et regler quoi que ce soit faisait
 * basculer la totalite d'un coup.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReglementPartielTests {

    @Autowired AchatService achatService;
    @Autowired FournisseurService fournisseurService;
    @Autowired ChantierService chantierService;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;
    @Autowired PaiementAchatRepository paiementAchatRepository;

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** L'exemple du client, aux ordres de grandeur pres. */
    @Test
    void payingPartOfADebtLeavesTheRestOutstanding() {
        Fixture f = newFactureeCommande("1000.00");          // 1 000 HT
        BigDecimal ttc = f.achat.ttc();

        achatService.reglerPartiellement(f.achat.id(), new BigDecimal("100.00"), ModePaiement.VIREMENT);

        FournisseurResponse four = fournisseurService.findById(f.fournisseurId);
        assertThat(four.soldeImpaye()).isEqualByComparingTo(ttc.subtract(new BigDecimal("100.00")));
    }

    /** Elle reste en FACTURE : un reglement partiel ne solde pas la commande. */
    @Test
    void aPartiallyPaidOrderIsNotSettled() {
        Fixture f = newFactureeCommande("1000.00");

        achatService.reglerPartiellement(f.achat.id(), new BigDecimal("100.00"), ModePaiement.VIREMENT);

        assertThat(achatService.findById(f.achat.id()).status().name()).isEqualTo("FACTURE");
    }

    /** Le dernier reglement solde, et bascule le statut de lui-meme. */
    @Test
    void theFinalInstalmentSettlesTheOrder() {
        Fixture f = newFactureeCommande("1000.00");
        BigDecimal ttc = f.achat.ttc();

        achatService.reglerPartiellement(f.achat.id(), new BigDecimal("400.00"), ModePaiement.VIREMENT);
        achatService.reglerPartiellement(f.achat.id(), ttc.subtract(new BigDecimal("400.00")), ModePaiement.VIREMENT);

        assertThat(achatService.findById(f.achat.id()).status().name()).isEqualTo("PAYE");
        assertThat(fournisseurService.findById(f.fournisseurId).soldeImpaye()).isEqualByComparingTo("0.00");
    }

    /** Chaque reglement laisse sa trace datee : c'est elle qui classe la periode. */
    @Test
    void eachInstalmentIsRecordedInTheLedger() {
        Fixture f = newFactureeCommande("1000.00");

        achatService.reglerPartiellement(f.achat.id(), new BigDecimal("300.00"), ModePaiement.VIREMENT);
        achatService.reglerPartiellement(f.achat.id(), new BigDecimal("200.00"), ModePaiement.CHEQUE);

        assertThat(paiementAchatRepository.findByAchatIdOrderByDatePaiementDesc(f.achat.id()))
                .hasSize(2)
                .allSatisfy(p -> assertThat(p.getDatePaiement()).isNotNull());
    }

    /** Solder en une fois passe par le meme chemin et laisse la meme trace. */
    @Test
    void settlingInOneGoAlsoLeavesALedgerEntry() {
        Fixture f = newFactureeCommande("1000.00");

        achatService.validatePaiement(f.achat.id(), ModePaiement.VIREMENT);

        assertThat(achatService.findById(f.achat.id()).status().name()).isEqualTo("PAYE");
        assertThat(paiementAchatRepository.findByAchatIdOrderByDatePaiementDesc(f.achat.id())).hasSize(1);
    }

    @Test
    void payingMoreThanWhatIsOwedIsRefused() {
        Fixture f = newFactureeCommande("1000.00");

        assertThatThrownBy(() -> achatService.reglerPartiellement(
                f.achat.id(), f.achat.ttc().add(BigDecimal.ONE), ModePaiement.VIREMENT))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void payingNothingIsRefused() {
        Fixture f = newFactureeCommande("1000.00");

        assertThatThrownBy(() -> achatService.reglerPartiellement(
                f.achat.id(), BigDecimal.ZERO, ModePaiement.VIREMENT))
                .isInstanceOf(BusinessRuleException.class);
    }

    /** Annuler efface le cumul et le registre, sinon la commande paraitrait soldee. */
    @Test
    void cancellingClearsTheInstalmentsAndTheLedger() {
        Fixture f = newFactureeCommande("1000.00");
        BigDecimal ttc = f.achat.ttc();
        achatService.validatePaiement(f.achat.id(), ModePaiement.VIREMENT);

        achatService.annulerPaiement(f.achat.id(), "essai");

        assertThat(achatService.findById(f.achat.id()).status().name()).isEqualTo("FACTURE");
        assertThat(fournisseurService.findById(f.fournisseurId).soldeImpaye()).isEqualByComparingTo(ttc);
        assertThat(paiementAchatRepository.findByAchatIdOrderByDatePaiementDesc(f.achat.id())).isEmpty();
    }

    // -- Fixtures ---------------------------------------------------

    private record Fixture(UUID fournisseurId, AchatResponse achat) {}

    private Fixture newFactureeCommande(String montantHt) {
        String suffix = "RP-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

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
                    c.setCode("RP" + (SEQ.get() % 1000));
                    c.setLibelle("Categorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-" + suffix);
        article.setDesignation("Article de test");
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(Double.parseDouble(montantHt));
        article.setTvaRate(new BigDecimal("20.00"));
        article = articleRepository.save(article);

        AchatResponse achat = achatService.create(new CreateAchatRequest(
                fournisseurId, chantierId, LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(article.getId(), 1.0, Double.parseDouble(montantHt), null)),
                true, false));

        achatService.validateBL(achat.id(), "BL-" + suffix);
        AchatResponse facturee = achatService.validateFacture(achat.id(), "FA-" + suffix);

        return new Fixture(fournisseurId, facturee);
    }
}
