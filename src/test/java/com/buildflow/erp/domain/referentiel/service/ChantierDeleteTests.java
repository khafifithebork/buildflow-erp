package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.service.AchatService;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.dto.request.CreateJalonRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ChantierResponse;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import com.buildflow.erp.domain.referentiel.entity.FournisseurStatut;
import com.buildflow.erp.domain.referentiel.entity.JalonStatut;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.referentiel.repository.FournisseurRepository;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateTransactionRequest;
import com.buildflow.erp.domain.tresorerie.entity.TypeTransaction;
import com.buildflow.erp.domain.tresorerie.repository.CaisseRepository;
import com.buildflow.erp.domain.tresorerie.repository.CaisseTransactionRepository;
import com.buildflow.erp.domain.tresorerie.service.TresorerieService;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reproduction + regression test for "a Chantier cannot be deleted".
 *
 * <p>Runs against a LOCAL throwaway Postgres only (see
 * {@code src/test/resources/application-test.yml}); every row it creates is
 * rolled back by {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChantierDeleteTests {

    @Autowired ChantierService chantierService;
    @Autowired ChantierRepository chantierRepository;
    @Autowired CaisseRepository caisseRepository;
    @Autowired CaisseTransactionRepository caisseTransactionRepository;
    @Autowired TresorerieService tresorerieService;
    @Autowired AchatService achatService;
    @Autowired FournisseurRepository fournisseurRepository;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;

    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * The original bug: create() auto-provisions a Caisse, so even a chantier
     * with no business data at all had a child row and DELETE was rejected.
     */
    @Test
    void deletesAFreshChantierTogetherWithItsAutoProvisionedCaisse() {
        ChantierResponse chantier = chantierService.create(newChantierRequest());
        UUID id = chantier.id();

        // Precondition: the service really did create a caisse behind our back.
        assertThat(caisseRepository.findByChantierId(id)).hasSize(1);

        assertThatCode(() -> chantierService.delete(id)).doesNotThrowAnyException();

        assertThat(chantierRepository.existsById(id)).isFalse();
        assertThat(caisseRepository.findByChantierId(id)).isEmpty();
    }

    /** Jalons are artifacts of the chantier and must not block deletion. */
    @Test
    void deletesAChantierThatHasJalons() {
        CreateChantierRequest request = newChantierRequest(List.of(
                // statut must be supplied: JalonMapper copies it verbatim and the
                // column is NOT NULL, so a null here fails on insert.
                new CreateJalonRequest("Terrassement", LocalDate.now().plusDays(10), null, JalonStatut.A_FAIRE)));
        UUID id = chantierService.create(request).id();

        assertThatCode(() -> chantierService.delete(id)).doesNotThrowAnyException();
        assertThat(chantierRepository.existsById(id)).isFalse();
    }

    /**
     * Cash operations are accounting history: deletion is refused, and the
     * message must say so rather than surfacing an opaque FK violation.
     */
    @Test
    void refusesToDeleteAChantierWithCaisseOperationsAndExplainsWhy() {
        UUID id = chantierService.create(newChantierRequest()).id();
        UUID caisseId = caisseRepository.findByChantierId(id).getFirst().getId();

        tresorerieService.enregistrerTransaction(caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal("1500.00"), "Approvisionnement",
                "REF-001", null, null, null));

        assertThatThrownBy(() -> chantierService.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ne peut pas être supprimé")
                .hasMessageContaining("1 opération de caisse");

        assertThat(chantierRepository.existsById(id)).isTrue();
    }

    /** Purchase orders block deletion and are named in the message. */
    @Test
    void refusesToDeleteAChantierWithAchatsAndExplainsWhy() {
        UUID id = chantierService.create(newChantierRequest()).id();
        createAchatOn(id);

        assertThatThrownBy(() -> chantierService.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 commande d'achat");

        assertThat(chantierRepository.existsById(id)).isTrue();
    }

    /**
     * The two new billing indicators default to false, survive a round-trip,
     * and propagate onto the caisse debit generated when an achat is paid.
     */
    @Test
    void impactFlagsDefaultToFalseAndArePersisted() {
        UUID chantierId = chantierService.create(newChantierRequest()).id();

        var withoutFlags = createAchatOn(chantierId);
        assertThat(withoutFlags.impactAnalytiqueChantier()).isFalse();
        assertThat(withoutFlags.impactComptableFiscal()).isFalse();

        var withFlags = createAchatOn(chantierId, true, true);
        assertThat(withFlags.impactAnalytiqueChantier()).isTrue();
        assertThat(withFlags.impactComptableFiscal()).isTrue();

        // Reload from the DB to prove the columns were actually written.
        var reloaded = achatService.findById(withFlags.id());
        assertThat(reloaded.impactAnalytiqueChantier()).isTrue();
        assertThat(reloaded.impactComptableFiscal()).isTrue();
    }

    // ── Fixtures ──────────────────────────────────────────────────────

    private CreateChantierRequest newChantierRequest() {
        return newChantierRequest(List.of());
    }

    private CreateChantierRequest newChantierRequest(List<CreateJalonRequest> jalons) {
        String suffix = "IT-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        return new CreateChantierRequest(
                "CH-" + suffix,
                "Chantier de test " + suffix,
                "Client de test",
                "1 rue du test",
                "Casablanca",
                ChantierStatut.EN_PREPARATION,
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                new BigDecimal("1000000.00"),
                "Chef de test",
                List.of(),
                jalons);
    }

    private com.buildflow.erp.domain.achats.dto.response.AchatResponse createAchatOn(UUID chantierId) {
        return createAchatOn(chantierId, null, null);
    }

    private com.buildflow.erp.domain.achats.dto.response.AchatResponse createAchatOn(
            UUID chantierId, Boolean analytique, Boolean fiscal) {

        String suffix = "IT-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        Fournisseur fournisseur = new Fournisseur();
        fournisseur.setCode("F-" + suffix);
        fournisseur.setRaisonSociale("Fournisseur " + suffix);
        fournisseur.setIce("ICE" + suffix);
        fournisseur.setStatut(FournisseurStatut.ACTIF);
        fournisseur = fournisseurRepository.save(fournisseur);

        // categories_articles.code is VARCHAR(10), so reuse an existing row when
        // there is one rather than minting a long unique code.
        CategorieArticle categorie = categorieArticleRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategorieArticle c = new CategorieArticle();
                    c.setCode("ITC" + (SEQ.get() % 1000));
                    c.setLibelle("Catégorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-" + suffix);
        article.setDesignation("Article de test");
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(new BigDecimal("100.00"));
        article.setTvaRate(new BigDecimal("20.00"));
        article = articleRepository.save(article);

        return achatService.create(new CreateAchatRequest(
                "ACH-" + suffix,
                fournisseur.getId(),
                chantierId,
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(
                        article.getId(), new BigDecimal("2"), new BigDecimal("100.00"), null)),
                analytique,
                fiscal));
    }
}
