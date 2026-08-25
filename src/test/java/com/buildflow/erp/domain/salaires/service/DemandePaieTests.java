package com.buildflow.erp.domain.salaires.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
import com.buildflow.erp.domain.salaires.dto.request.CreateDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.request.PayerDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.response.DemandePaieResponse;
import com.buildflow.erp.domain.salaires.entity.DemandePaieStatut;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Demande de paie: the payroll disbursement that has no payslip behind it.
 *
 * <p>The rules worth pinning down are the ones that move money or destroy a
 * record: only CAISSE touches the chantier's cash, cash needs a chantier to
 * come out of, and a paid demande cannot be deleted.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemandePaieTests {

    @Autowired DemandePaieService demandePaieService;
    @Autowired ChantierService chantierService;
    @Autowired CaisseRepository caisseRepository;
    @Autowired TresorerieService tresorerieService;

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Test
    void aNewDemandeIsSubmittedAndNumbered() {
        DemandePaieResponse demande = newDemande(null, "1200.00");

        assertThat(demande.statut()).isEqualTo(DemandePaieStatut.SOUMISE);
        assertThat(demande.reference()).startsWith("DP-2026-07-");
        assertThat(demande.montantNet()).isEqualByComparingTo("1200.00");
        assertThat(demande.modePaiement()).isNull();
        assertThat(demande.createdAt()).isNotNull();
    }

    /** The amount is an input here, carried through untouched. */
    @Test
    void theAmountIsNotRecomputed() {
        assertThat(newDemande(null, "3333.33").montantNet()).isEqualByComparingTo("3333.33");
    }

    @Test
    void aChantierIsOptional() {
        assertThat(newDemande(null, "500.00").chantierId()).isNull();
    }

    @Test
    void submittedBecomesValidated() {
        DemandePaieResponse demande = newDemande(null, "800.00");

        assertThat(demandePaieService.valider(demande.id()).statut())
                .isEqualTo(DemandePaieStatut.VALIDEE);
    }

    @Test
    void aDemandeCannotBePaidBeforeItIsValidated() {
        DemandePaieResponse demande = newDemande(null, "800.00");

        assertThatThrownBy(() -> demandePaieService.payer(
                demande.id(), new PayerDemandePaieRequest(ModePaiement.VIREMENT)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aDemandeCannotBeValidatedTwice() {
        DemandePaieResponse demande = newDemande(null, "800.00");
        demandePaieService.valider(demande.id());

        assertThatThrownBy(() -> demandePaieService.valider(demande.id()))
                .isInstanceOf(BusinessRuleException.class);
    }

    /** Same rule as a fiche de paie: a virement settles through the bank. */
    @Test
    void aVirementLeavesTheCaisseAlone() {
        UUID chantierId = newChantier();
        UUID caisseId = caisseId(chantierId);
        BigDecimal before = solde(caisseId);

        DemandePaieResponse demande = newDemande(chantierId, "900.00");
        demandePaieService.valider(demande.id());
        DemandePaieResponse paid = demandePaieService.payer(
                demande.id(), new PayerDemandePaieRequest(ModePaiement.VIREMENT));

        assertThat(paid.statut()).isEqualTo(DemandePaieStatut.PAYEE);
        assertThat(paid.modePaiement()).isEqualTo(ModePaiement.VIREMENT);
        assertThat(solde(caisseId)).isEqualByComparingTo(before);
    }

    @Test
    void cashComesOutOfTheChantiersCaisse() {
        UUID chantierId = newChantier();
        UUID caisseId = caisseId(chantierId);
        // A fresh caisse is empty and the ledger refuses to go negative, so the
        // cash has to be there before it can be paid out.
        crediter(caisseId, "5000.00");
        BigDecimal before = solde(caisseId);

        DemandePaieResponse demande = newDemande(chantierId, "900.00");
        demandePaieService.valider(demande.id());
        demandePaieService.payer(demande.id(), new PayerDemandePaieRequest(ModePaiement.CAISSE));

        assertThat(solde(caisseId)).isEqualByComparingTo(before.subtract(new BigDecimal("900.00")));
    }

    /**
     * Without a chantier there is no caisse to debit. Paying cash anyway would
     * leave every balance overstating what is actually left.
     */
    @Test
    void cashWithoutAChantierIsRefused() {
        DemandePaieResponse demande = newDemande(null, "900.00");
        demandePaieService.valider(demande.id());

        assertThatThrownBy(() -> demandePaieService.payer(
                demande.id(), new PayerDemandePaieRequest(ModePaiement.CAISSE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("chantier");
    }

    /** The ledger refuses to go negative, whatever the document asking. */
    @Test
    void cashBeyondWhatTheCaisseHoldsIsRefused() {
        UUID chantierId = newChantier();
        crediter(caisseId(chantierId), "100.00");

        DemandePaieResponse demande = newDemande(chantierId, "900.00");
        demandePaieService.valider(demande.id());

        assertThatThrownBy(() -> demandePaieService.payer(
                demande.id(), new PayerDemandePaieRequest(ModePaiement.CAISSE)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anUnpaidDemandeCanBeDeleted() {
        DemandePaieResponse demande = newDemande(null, "400.00");

        demandePaieService.delete(demande.id());

        assertThat(demandePaieService.findAll())
                .extracting(DemandePaieResponse::id)
                .doesNotContain(demande.id());
    }

    /** A paid demande has moved money and is referenced by the audit trail. */
    @Test
    void aPaidDemandeCannotBeDeleted() {
        DemandePaieResponse demande = newDemande(null, "400.00");
        demandePaieService.valider(demande.id());
        demandePaieService.payer(demande.id(), new PayerDemandePaieRequest(ModePaiement.VIREMENT));

        assertThatThrownBy(() -> demandePaieService.delete(demande.id()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theListIsFilteredByPeriode() {
        DemandePaieResponse july = newDemande(null, "100.00");

        assertThat(demandePaieService.findByPeriode("2026-07"))
                .extracting(DemandePaieResponse::id)
                .contains(july.id());
        assertThat(demandePaieService.findByPeriode("2019-01"))
                .extracting(DemandePaieResponse::id)
                .doesNotContain(july.id());
    }

    // -- Fixtures ---------------------------------------------------

    private DemandePaieResponse newDemande(UUID chantierId, String montant) {
        return demandePaieService.create(new CreateDemandePaieRequest(
                "Main d'oeuvre " + SEQ.incrementAndGet(),
                "2026-07",
                chantierId,
                null,
                new BigDecimal(montant)));
    }

    private UUID newChantier() {
        String suffix = "DP-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        return chantierService.create(new CreateChantierRequest(
                "Chantier " + suffix, "Client", "Adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(6),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();
    }

    private UUID caisseId(UUID chantierId) {
        return caisseRepository.findByChantierId(chantierId).getFirst().getId();
    }

    private void crediter(UUID caisseId, String montant) {
        tresorerieService.enregistrerTransaction(caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal(montant), "Approvisionnement de test",
                null, null, null, null));
    }

    private BigDecimal solde(UUID caisseId) {
        return caisseRepository.findById(caisseId).orElseThrow().getSolde();
    }
}
