package com.buildflow.erp.domain.bpu.service;

import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.bpu.dto.request.CreateBpuLigneRequest;
import com.buildflow.erp.domain.bpu.dto.response.BpuLigneResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
import com.buildflow.erp.domain.salaires.dto.request.CreateDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.request.PayerDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.response.DemandePaieResponse;
import com.buildflow.erp.domain.salaires.service.DemandePaieService;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateTransactionRequest;
import com.buildflow.erp.domain.tresorerie.entity.TypeTransaction;
import com.buildflow.erp.domain.tresorerie.repository.CaisseRepository;
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

/**
 * Ce que le bordereau compte comme engagé, et sur quelle base.
 *
 * <p>Le bordereau se tient en hors taxes, mais deux de ses quatre sources ne
 * portent aucune taxe : la paie et la caisse. Les convertir en HT reviendrait à
 * retrancher une TVA qui n'a jamais été payée.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BpuConsommationTests {

    @Autowired BpuLigneService bpuLigneService;
    @Autowired ChantierService chantierService;
    @Autowired TresorerieService tresorerieService;
    @Autowired DemandePaieService demandePaieService;
    @Autowired CaisseRepository caisseRepository;

    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * Le cas qui a motivé le correctif : la caisse était divisée par 1,20, ce
     * qui affichait 833,33 pour 1 000 réellement sortis du tiroir.
     */
    @Test
    void cashIsCountedAtFaceValue() {
        Fixture f = newLigne();
        depenserEnEspeces(f, "1000.00");

        assertThat(engage(f)).isEqualByComparingTo("1000.00");
    }

    /** Et surtout : pas la valeur qu'aurait donnée l'ancienne conversion. */
    @Test
    void cashIsNotDividedByAnyVatRate() {
        Fixture f = newLigne();
        depenserEnEspeces(f, "1000.00");

        assertThat(engage(f))
                .isNotEqualByComparingTo("833.33")   // ancien 1,20
                .isNotEqualByComparingTo("909.09");  // et pas davantage le 1,10 courant
    }

    @Test
    void severalCashEntriesAddUpUntouched() {
        Fixture f = newLigne();
        depenserEnEspeces(f, "250.00");
        depenserEnEspeces(f, "125.50");

        assertThat(engage(f)).isEqualByComparingTo("375.50");
    }

    /**
     * Une demande de paie payee compte dans la ligne du bordereau.
     *
     * <p>Le formulaire laissait choisir une ligne BPU depuis la creation de la
     * fonctionnalite, et rien ne la lisait : le bordereau sous-estimait d'autant
     * la paie imputee.
     */
    @Test
    void aPaidDemandeDePaieCountsTowardsTheLine() {
        Fixture f = newLigne();
        BigDecimal avant = engage(f);

        payerDemande(f, "700.00");

        assertThat(engage(f)).isEqualByComparingTo(avant.add(new BigDecimal("700.00")));
    }

    /**
     * Non reglee, elle compte quand meme : le seuil est l'engagement.
     *
     * <p>Le bordereau mesure ce qui est engage contre un budget, pas ce qui est
     * sorti. Une demande imputee a une ligne engage son montant des sa
     * soumission — elle n'a d'ailleurs pas d'etat brouillon.
     */
    @Test
    void anUnpaidDemandeDePaieCountsToo() {
        Fixture f = newLigne();
        BigDecimal avant = engage(f);

        demandePaieService.create(new CreateDemandePaieRequest(
                "Demande non reglee", "2026-07", f.chantierId, f.ligneId, new BigDecimal("500.00")));

        assertThat(engage(f)).isEqualByComparingTo(avant.add(new BigDecimal("500.00")));
    }

    @Test
    void aLineWithNothingImputedIsAtZero() {
        assertThat(engage(newLigne())).isEqualByComparingTo("0.00");
    }

    /** La consommation se mesure contre le budget prévu, lui aussi HT. */
    @Test
    void theConsumptionRatioFollowsTheUntouchedAmount() {
        Fixture f = newLigne();          // 10 × 100 = 1 000 de budget
        depenserEnEspeces(f, "500.00");

        assertThat(engage(f)).isEqualByComparingTo("500.00");
        assertThat(ligne(f).tauxConsommation()).isEqualByComparingTo("0.5000");
        assertThat(ligne(f).alerteDepassement()).isFalse();
    }

    @Test
    void spendingPastTheBudgetRaisesTheAlert() {
        Fixture f = newLigne();          // budget 1 000
        depenserEnEspeces(f, "1500.00");

        assertThat(ligne(f).alerteDepassement()).isTrue();
    }

    // ── Fixtures ───────────────────────────────────────────────────

    private record Fixture(UUID chantierId, UUID ligneId, UUID caisseId) {}

    private Fixture newLigne() {
        String suffix = "BPU-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        UUID chantierId = chantierService.create(new CreateChantierRequest(
                "Chantier " + suffix, "Client", "Adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(6),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();

        UUID ligneId = bpuLigneService.create(chantierId, new CreateBpuLigneRequest(
                "1.1", "Main d'oeuvre " + suffix, "U", new BigDecimal("10"), 100.0)).id();

        UUID caisseId = caisseRepository.findByChantierId(chantierId).getFirst().getId();
        return new Fixture(chantierId, ligneId, caisseId);
    }

    /** Une sortie d'espèces imputée à la ligne, la caisse étant approvisionnée d'abord. */
    private void depenserEnEspeces(Fixture f, String montant) {
        tresorerieService.enregistrerTransaction(f.caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal(montant), "Approvisionnement",
                null, null, null, null));
        tresorerieService.enregistrerTransaction(f.caisseId, new CreateTransactionRequest(
                TypeTransaction.DEBIT, new BigDecimal(montant), "Dépense espèces",
                null, f.ligneId, null, null));
    }

    /** Une demande imputee a la ligne, validee puis reglee par virement. */
    private void payerDemande(Fixture f, String montant) {
        DemandePaieResponse d = demandePaieService.create(new CreateDemandePaieRequest(
                "Demande " + SEQ.incrementAndGet(), "2026-07", f.chantierId, f.ligneId,
                new BigDecimal(montant)));
        demandePaieService.valider(d.id());
        demandePaieService.payer(d.id(), new PayerDemandePaieRequest(ModePaiement.VIREMENT));
    }

    private BpuLigneResponse ligne(Fixture f) {
        return bpuLigneService.findByChantier(f.chantierId).stream()
                .filter(l -> l.id().equals(f.ligneId))
                .findFirst()
                .orElseThrow();
    }

    private BigDecimal engage(Fixture f) {
        return ligne(f).montantEngageHt();
    }
}
