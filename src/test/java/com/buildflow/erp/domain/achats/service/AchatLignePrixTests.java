package com.buildflow.erp.domain.achats.service;

import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateTransactionRequest;
import com.buildflow.erp.domain.tresorerie.entity.TypeTransaction;
import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.tresorerie.entity.CaisseTransaction;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.UpdateLignePrixRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.entity.AchatStatut;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import com.buildflow.erp.domain.referentiel.entity.FournisseurStatut;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.FournisseurRepository;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Re-pricing an order line. Runs against a LOCAL throwaway Postgres; every row
 * is rolled back by {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AchatLignePrixTests {

    @Autowired AchatService achatService;
    @Autowired ChantierService chantierService;
    @Autowired FournisseurRepository fournisseurRepository;
    @Autowired ArticleRepository articleRepository;
    @Autowired CategorieArticleRepository categorieArticleRepository;
    @Autowired com.buildflow.erp.domain.tresorerie.repository.CaisseRepository caisseRepository;
    @Autowired com.buildflow.erp.domain.tresorerie.service.TresorerieService tresorerieService;
    @Autowired com.buildflow.erp.domain.tresorerie.repository.CaisseTransactionRepository caisseTransactionRepository;
    @Autowired com.buildflow.erp.domain.stock.repository.StockArticleRepository stockArticleRepository;
    @Autowired com.buildflow.erp.domain.stock.service.StockService stockService;
    @Autowired AchatRepository achatRepository;

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Test
    void repricingALineRecomputesTheLineTotalAndTheOrderTotals() {
        // 2 × 100 = 200 HT, +10% TVA = 220 TTC
        AchatResponse achat = createAchat(2, 100.00);
        assertThat(achat.ht()).isEqualByComparingTo("200.00");
        assertThat(achat.ttc()).isEqualByComparingTo("220.00");

        UUID ligneId = achat.lignes().getFirst().id();

        // Re-price to 250 → 2 × 250 = 500 HT, TVA 50, TTC 550
        AchatResponse updated = achatService.updateLignePrix(
                achat.id(), ligneId, new UpdateLignePrixRequest(250.00)).achat();

        assertThat(updated.lignes().getFirst().prixUnitaire()).isEqualTo(250.00);
        assertThat(updated.lignes().getFirst().total()).isEqualByComparingTo("500.00");
        assertThat(updated.ht()).isEqualByComparingTo("500.00");
        assertThat(updated.tva()).isEqualByComparingTo("50.00");
        assertThat(updated.ttc()).isEqualByComparingTo("550.00");
    }

    @Test
    void repricingSurvivesAReload() {
        AchatResponse achat = createAchat(3, 10.00);
        UUID ligneId = achat.lignes().getFirst().id();

        achatService.updateLignePrix(achat.id(), ligneId, new UpdateLignePrixRequest(11.50));

        AchatResponse reloaded = achatService.findById(achat.id());
        assertThat(reloaded.lignes().getFirst().prixUnitaire()).isEqualTo(11.50);
        assertThat(reloaded.ht()).isEqualByComparingTo("34.50");
    }

    @Test
    void repricingToZeroIsAllowedAndZeroesTheOrder() {
        AchatResponse achat = createAchat(4, 25.00);
        UUID ligneId = achat.lignes().getFirst().id();

        AchatResponse updated = achatService.updateLignePrix(
                achat.id(), ligneId, new UpdateLignePrixRequest(0.0)).achat();

        assertThat(updated.ht()).isEqualByComparingTo("0.00");
        assertThat(updated.tva()).isEqualByComparingTo("0.00");
        assertThat(updated.ttc()).isEqualByComparingTo("0.00");
    }

    @Test
    void aLineFromAnotherOrderIsNotFound() {
        AchatResponse first = createAchat(1, 10.00);
        AchatResponse second = createAchat(1, 10.00);

        UUID foreignLigneId = second.lignes().getFirst().id();

        assertThatThrownBy(() -> achatService.updateLignePrix(
                first.id(), foreignLigneId, new UpdateLignePrixRequest(99.00)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Nothing downstream has happened yet at EN_COURS / LIVRE, so no warning. */
    @Test
    void noWarningBeforeInvoicing() {
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.EN_COURS, AchatService.Revalorisation.COMPLETE)).isNull();
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.LIVRE, AchatService.Revalorisation.COMPLETE)).isNull();
    }

    /** Invoiced or paid, re-pricing leaves something out of step — say so. */
    @Test
    void warningOnceInvoicedOrPaid() {
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.FACTURE, AchatService.Revalorisation.COMPLETE))
                .isNotNull()
                .contains("facture");
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.PAYE, AchatService.Revalorisation.COMPLETE))
                .isNotNull()
                .contains("caisse");
    }

    /**
     * The point of moving prices to DOUBLE PRECISION: a unit price may now carry
     * more than two decimals, and it survives the round-trip instead of being
     * truncated to centimes on the way into the database.
     */
    @Test
    void aUnitPriceKeepsMoreThanTwoDecimals() {
        AchatResponse achat = createAchat(1000, 0.1234);

        assertThat(achat.lignes().getFirst().prixUnitaire()).isEqualTo(0.1234);
        assertThat(achatService.findById(achat.id()).lignes().getFirst().prixUnitaire())
                .isEqualTo(0.1234);
    }

    /**
     * The extra precision is used in the calculation, but the line total is
     * still rounded to two decimals — that is the figure that gets invoiced.
     */
    @Test
    void theLineTotalIsStillRoundedToCentimes() {
        // 3 × 10.005 = 30.015 → 30.02 HALF_UP
        AchatResponse achat = createAchat(3, 10.005);

        assertThat(achat.lignes().getFirst().total()).isEqualByComparingTo("30.02");
        assertThat(achat.ht()).isEqualByComparingTo("30.02");
        assertThat(achat.ttc()).isEqualByComparingTo("33.02");
    }

    /** Re-pricing accepts sub-centime values too. */
    @Test
    void repricingAcceptsMoreThanTwoDecimals() {
        AchatResponse achat = createAchat(200, 1.00);
        UUID ligneId = achat.lignes().getFirst().id();

        AchatResponse updated = achatService.updateLignePrix(
                achat.id(), ligneId, new UpdateLignePrixRequest(0.3333)).achat();

        assertThat(updated.lignes().getFirst().prixUnitaire()).isEqualTo(0.3333);
        // 200 × 0.3333 = 66.66
        assertThat(updated.ht()).isEqualByComparingTo("66.66");
    }

    /**
     * Quantities are DOUBLE PRECISION too, so a line can be ordered in
     * fractional units without the value being rounded to three decimals.
     */
    @Test
    void aQuantityKeepsMoreThanThreeDecimals() {
        AchatResponse achat = createAchat(2.12345, 100.00);

        assertThat(achat.lignes().getFirst().quantite()).isEqualTo(2.12345);
        assertThat(achatService.findById(achat.id()).lignes().getFirst().quantite())
                .isEqualTo(2.12345);
        // 2.12345 x 100 = 212.345 -> 212.35 HALF_UP
        assertThat(achat.ht()).isEqualByComparingTo("212.35");
    }

    /**
     * Regression: re-pricing an order already settled from the caisse used to
     * move every derived figure while the cash ledger stayed on the amount
     * actually paid, so the margins drifted for free. The difference is now
     * posted back to the caisse as its own movement.
     */
    @Test
    void repricingAPaidOrderReconcilesTheCaisse() {
        AchatResponse achat = createAchat(10, 100.00);   // 1000 HT / 1100 TTC
        UUID chantierId = chantierIdOf(achat);
        UUID caisseId = caisseRepository.findByChantierId(chantierId).getFirst().getId();

        tresorerieService.enregistrerTransaction(caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal("5000.00"), "Appro", null, null, null, null));

        achatService.validateBL(achat.id(), "BL");
        achatService.validateFacture(achat.id(), "FA");
        achatService.validatePaiement(achat.id(), ModePaiement.CAISSE);

        BigDecimal soldeApresPaiement = caisseRepository.findById(caisseId).orElseThrow().getSolde();
        assertThat(soldeApresPaiement).isEqualByComparingTo("3900.00");   // 5000 - 1100

        // Halve the price: the order drops to 550 TTC, so 550 comes back.
        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(50.00));

        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("4450.00");                          // 5000 - 550
    }

    /** Raising the price on a settled order takes the extra out of the caisse. */
    @Test
    void repricingUpwardDebitsTheDifference() {
        AchatResponse achat = createAchat(10, 100.00);
        UUID caisseId = caisseRepository.findByChantierId(chantierIdOf(achat)).getFirst().getId();

        tresorerieService.enregistrerTransaction(caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal("5000.00"), "Appro", null, null, null, null));
        achatService.validateBL(achat.id(), "BL");
        achatService.validateFacture(achat.id(), "FA");
        achatService.validatePaiement(achat.id(), ModePaiement.CAISSE);

        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        // 10 x 150 = 1500 HT -> 1650 TTC, so 550 more leaves the caisse.
        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("3350.00");                          // 5000 - 1650
    }

    /** A virement never touched the caisse, so re-pricing must not either. */
    @Test
    void repricingAnOrderPaidByVirementLeavesTheCaisseAlone() {
        AchatResponse achat = createAchat(10, 100.00);
        UUID caisseId = caisseRepository.findByChantierId(chantierIdOf(achat)).getFirst().getId();

        achatService.validateBL(achat.id(), "BL");
        achatService.validateFacture(achat.id(), "FA");
        achatService.validatePaiement(achat.id(), ModePaiement.VIREMENT);

        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(50.00));

        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("0.00");
    }

    /**
     * Regression: the caisse balance being right is not enough. The
     * décaissements KPI summed DEBIT rows only, so the refund posted when a
     * settled order was re-priced down never came off the total — an order
     * paid 1200 and refunded 600 still reported 1200 of spend.
     *
     * Asserted on the repository the KPI reads, in both directions, because a
     * delta of zero can hide an absolute figure that is wrong.
     */
    @Test
    void decaissementsFollowTheCashAfterAReprice() {
        AchatResponse achat = createAchat(10, 100.00);        // 1100 TTC
        UUID chantierId = chantierIdOf(achat);
        UUID caisseId = caisseRepository.findByChantierId(chantierId).getFirst().getId();

        tresorerieService.enregistrerTransaction(caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal("10000.00"), "Appro", null, null, null, null));

        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now().plusDays(1);
        BigDecimal avant = caisseTransactionRepository.sumDebitsBetween(from, to);

        achatService.validateBL(achat.id(), "BL");
        achatService.validateFacture(achat.id(), "FA");
        achatService.validatePaiement(achat.id(), ModePaiement.CAISSE);

        assertThat(caisseTransactionRepository.sumDebitsBetween(from, to).subtract(avant))
                .as("the 1100 paid")
                .isEqualByComparingTo("1100.00");

        // Down to 550 TTC: 550 comes back, so only 550 net has left.
        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(50.00));
        assertThat(caisseTransactionRepository.sumDebitsBetween(from, to).subtract(avant))
                .as("net after the refund")
                .isEqualByComparingTo("550.00");

        // Up to 1650 TTC: 1100 more leaves, so 1650 net.
        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));
        assertThat(caisseTransactionRepository.sumDebitsBetween(from, to).subtract(avant))
                .as("net after the increase")
                .isEqualByComparingTo("1650.00");
    }

    /** Funding the caisse is money in, and must never reduce décaissements. */
    @Test
    void anOrdinaryCreditIsNotNettedOutOfDecaissements() {
        AchatResponse achat = createAchat(10, 100.00);
        UUID caisseId = caisseRepository.findByChantierId(chantierIdOf(achat)).getFirst().getId();

        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now().plusDays(1);
        BigDecimal avant = caisseTransactionRepository.sumDebitsBetween(from, to);

        tresorerieService.enregistrerTransaction(caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal("10000.00"), "Approvisionnement",
                null, null, null, null));

        assertThat(caisseTransactionRepository.sumDebitsBetween(from, to))
                .as("a plain credit leaves décaissements alone")
                .isEqualByComparingTo(avant);
    }

    /** The ref is server-assigned now, and follows the ACH-<year>-NNN shape. */
    @Test
    void createdOrderGetsAGeneratedRef() {
        AchatResponse achat = createAchat(1, 1.00);
        assertThat(achat.ref()).matches("^ACH-\\d{4}-\\d{3,}$");
    }

    // ── Taux de TVA ───────────────────────────────────────────────────

    /**
     * Régression : {@code articles.tva_rate} était saisi, validé, exporté — et
     * ignoré, la taxe étant calculée avec une constante. Tant que le catalogue
     * entier partage le taux de la constante, les deux nombres coïncident et
     * rien ne se voit. Ce test les décale exprès.
     */
    @Test
    void laTvaSuitLeTauxDeLArticleEtNonUneConstante() {
        AchatResponse achat = createAchat(10, 100.00, 100.00, new BigDecimal("14.00"));

        assertThat(achat.ht()).isEqualByComparingTo("1000.00");
        assertThat(achat.tva()).isEqualByComparingTo("140.00");
        assertThat(achat.ttc()).isEqualByComparingTo("1140.00");
    }

    /** Une commande peut mélanger des taux ; chaque ligne compte avec le sien. */
    @Test
    void uneCommandeMelangeantDeuxTauxLesAppliqueLigneParLigne() {
        AchatResponse achat = createAchatDeuxTaux(
                10, 100.00, new BigDecimal("20.00"),     // 1000 HT -> 200
                10, 100.00, new BigDecimal("7.00"));     // 1000 HT ->  70

        assertThat(achat.ht()).isEqualByComparingTo("2000.00");
        assertThat(achat.tva()).isEqualByComparingTo("270.00");
        assertThat(achat.ttc()).isEqualByComparingTo("2270.00");
    }

    /**
     * Le taux est figé à la commande, pas relu chez l'article : une commande
     * facturée à 14 % ne se réécrit pas parce que le référentiel a changé.
     */
    @Test
    void leTauxResteCeluiDeLaCommandeQuandLArticleChange() {
        AchatResponse achat = createAchat(10, 100.00, 100.00, new BigDecimal("14.00"));
        Article article = articleRepository.findByCode(
                achat.lignes().getFirst().articleCode()).orElseThrow();

        article.setTvaRate(new BigDecimal("20.00"));
        articleRepository.saveAndFlush(article);

        // une re-tarification recalcule les totaux : le taux d'origine doit tenir
        AchatResponse apres = achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(200.00)).achat();

        assertThat(apres.ht()).isEqualByComparingTo("2000.00");
        assertThat(apres.tva()).as("toujours 14 %").isEqualByComparingTo("280.00");
    }

    /** Le taux voyage jusqu'à la ligne renvoyée, en fraction. */
    @Test
    void leTauxFigeEstVisibleSurLaLigne() {
        AchatResponse achat = createAchat(1, 100.00, 100.00, new BigDecimal("7.00"));

        assertThat(achat.lignes().getFirst().tvaRate()).isEqualByComparingTo("0.0700");
    }

    // ── Annulation d'un paiement ──────────────────────────────────────

    /**
     * Régression : contre-passer l'écriture de règlement depuis la caisse
     * rendait l'argent en laissant la commande PAYE. La dette ne réapparaissait
     * pas et le décaissement disparaissait — la commande devenait gratuite au
     * bilan. Une écriture qui règle un document ne s'annule plus seule.
     */
    @Test
    void uneEcritureDeReglementNeSAnnulePasDepuisLaCaisse() {
        AchatResponse achat = createAchat(10, 100.00);
        UUID caisseId = approvisionner(achat, "5000.00");
        payerParCaisse(achat);

        CaisseTransaction reglement = ecritureDeReglement(caisseId, achat);

        assertThatThrownBy(() ->
                tresorerieService.annulerTransaction(caisseId, reglement.getId(), "erreur"))
                .hasMessageContaining("depuis la commande");
    }

    /** Le bon chemin défait les deux faces : le statut et l'argent. */
    @Test
    void annulerLePaiementRendLArgentEtRemetLaCommandeEnDette() {
        AchatResponse achat = createAchat(10, 100.00);       // 1000 HT / 1100 TTC
        UUID caisseId = approvisionner(achat, "5000.00");
        payerParCaisse(achat);

        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("3900.00");
        BigDecimal detteQuandPayee = achatRepository.sumHtNonPayees();

        AchatResponse annule = achatService.annulerPaiement(achat.id(), "saisie par erreur");

        assertThat(annule.status()).isEqualTo(AchatStatut.FACTURE);
        assertThat(annule.modePaiement()).isNull();
        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .as("les 1100 sont revenus")
                .isEqualByComparingTo("5000.00");
        assertThat(achatRepository.sumHtNonPayees().subtract(detteQuandPayee))
                .as("la dette fournisseur réapparaît")
                .isEqualByComparingTo("1000.00");
    }

    /** Et les décaissements retombent à zéro net, pas seulement le solde. */
    @Test
    void annulerLePaiementSortLaCommandeDesDecaissements() {
        AchatResponse achat = createAchat(10, 100.00);
        approvisionner(achat, "5000.00");
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now().plusDays(1);
        BigDecimal avant = caisseTransactionRepository.sumDebitsBetween(from, to);

        payerParCaisse(achat);
        achatService.annulerPaiement(achat.id(), "erreur");

        assertThat(caisseTransactionRepository.sumDebitsBetween(from, to))
                .isEqualByComparingTo(avant);
    }

    /**
     * Un changement de prix après le règlement laisse une écriture d'ajustement.
     * Elle appartient au même document et doit partir avec lui, sinon la caisse
     * garde la différence.
     */
    @Test
    void annulerLePaiementEmporteAussiLesAjustements() {
        AchatResponse achat = createAchat(10, 100.00);
        UUID caisseId = approvisionner(achat, "5000.00");
        payerParCaisse(achat);

        // re-tarifé à la hausse : 550 de plus sortent de la caisse
        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));
        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("3350.00");

        achatService.annulerPaiement(achat.id(), "erreur");

        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .as("règlement et ajustement rendus tous les deux")
                .isEqualByComparingTo("5000.00");
    }

    /** Un virement ne sort pas de la caisse, son annulation n'y rentre pas non plus. */
    @Test
    void annulerUnPaiementParVirementLaisseLaCaisseTranquille() {
        AchatResponse achat = createAchat(10, 100.00);
        UUID caisseId = approvisionner(achat, "5000.00");
        achatService.validateBL(achat.id(), "BL");
        achatService.validateFacture(achat.id(), "FA");
        achatService.validatePaiement(achat.id(), ModePaiement.VIREMENT);

        achatService.annulerPaiement(achat.id(), "erreur");

        assertThat(achatService.findById(achat.id()).status()).isEqualTo(AchatStatut.FACTURE);
        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("5000.00");
    }

    /** Rien à annuler sur une commande qui n'a jamais été réglée. */
    @Test
    void onNAnnulePasLePaiementDUneCommandeNonPayee() {
        AchatResponse achat = createAchat(10, 100.00);

        assertThatThrownBy(() -> achatService.annulerPaiement(achat.id(), "erreur"))
                .hasMessageContaining("pas payée");
    }

    /** Annulée puis re-réglée : le cycle se referme proprement. */
    @Test
    void uneCommandeAnnuleePeutEtreRepayee() {
        AchatResponse achat = createAchat(10, 100.00);
        UUID caisseId = approvisionner(achat, "5000.00");
        payerParCaisse(achat);
        achatService.annulerPaiement(achat.id(), "erreur");

        AchatResponse repayee = achatService.validatePaiement(achat.id(), ModePaiement.CAISSE);

        assertThat(repayee.status()).isEqualTo(AchatStatut.PAYE);
        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .isEqualByComparingTo("3900.00");
    }

    private UUID approvisionner(AchatResponse achat, String montant) {
        UUID caisseId = caisseRepository.findByChantierId(chantierIdOf(achat)).getFirst().getId();
        tresorerieService.enregistrerTransaction(caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal(montant), "Appro", null, null, null, null));
        return caisseId;
    }

    private void payerParCaisse(AchatResponse achat) {
        achatService.validateBL(achat.id(), "BL");
        achatService.validateFacture(achat.id(), "FA");
        achatService.validatePaiement(achat.id(), ModePaiement.CAISSE);
    }

    private CaisseTransaction ecritureDeReglement(UUID caisseId, AchatResponse achat) {
        return caisseTransactionRepository.findByCaisseIdOrderByCreatedAtDesc(caisseId).stream()
                .filter(t -> achat.ref().equals(t.getReferenceDocument()) && !t.isAjustement())
                .findFirst().orElseThrow();
    }

    // ── Stock valuation ───────────────────────────────────────────────

    /**
     * Regression, the écart users were reporting: re-pricing a commande whose
     * goods had already arrived moved every cash figure but left the stock
     * valued at the old price, so the marge nette dropped by qté × Δprix — 500
     * on ten units re-priced from 100 to 150 — with nothing to explain it.
     */
    @Test
    void repricingAReceivedOrderRevaluesItsStock() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");

        BigDecimal apresReception = valeurStock();

        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        assertThat(valeurStock().subtract(apresReception))
                .as("10 units re-priced by +50 are worth 500 more")
                .isEqualByComparingTo("500.00");
    }

    /** And down again, symmetrically. */
    @Test
    void repricingDownwardRevaluesStockDownward() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");
        BigDecimal apresReception = valeurStock();

        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(60.00));

        assertThat(valeurStock().subtract(apresReception)).isEqualByComparingTo("-400.00");
    }

    /** Nothing has been received yet, so there is nothing to re-value. */
    @Test
    void repricingBeforeReceiptLeavesStockAlone() {
        AchatResponse achat = createAchat(10, 100.00);
        BigDecimal avant = valeurStock();

        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        assertThat(valeurStock()).isEqualByComparingTo(avant);
    }

    /**
     * Stock is worth what was paid for it. The article's reference price is a
     * catalogue figure — an order placed above or below it must not be valued
     * at the catalogue.
     */
    @Test
    void stockIsValuedAtWhatWasPaidNotAtTheArticleReference() {
        BigDecimal avant = valeurStock();
        // list price 100, this order pays 120
        AchatResponse achat = createAchat(10, 120.00, 100.00);
        achatService.validateBL(achat.id(), "BL");

        assertThat(valeurStock().subtract(avant)).isEqualByComparingTo("1200.00");
    }

    /**
     * The correction reaches only the units still held. Ten received and five
     * consumed, re-priced 100 → 150: the five left are worth 250 more. The five
     * consumed went out at 100 and that cost is spent — putting the whole 500
     * back would price the remainder at 200 a unit, which nobody paid.
     */
    @Test
    void onlyTheUnitsStillHeldAreRevalued() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");
        consommer(achat, 5);

        BigDecimal avant = valeurStock();
        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        assertThat(valeurStock().subtract(avant))
                .as("5 units left, worth 50 more each")
                .isEqualByComparingTo("250.00");
    }

    /** And the re-priced line is left at the price paid, not at an inflated one. */
    @Test
    void aPartlyConsumedLineKeepsAnHonestUnitCost() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");
        consommer(achat, 5);

        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        assertThat(ligneStock(achat).getCoutUnitaire())
                .as("the 5 left cost 150 each, not 200")
                .isEqualTo(150.0);
    }

    /**
     * Downward, on a part-consumed line, is where the old arithmetic went
     * negative and got clamped to zero — landing on a plausible number for the
     * wrong reason. Five units re-priced 100 → 50 are worth 250 less, and the
     * unit cost must be 50 rather than a floored 0.
     */
    @Test
    void aPartlyConsumedLineIsNotClampedToZeroOnTheWayDown() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");
        consommer(achat, 5);

        BigDecimal avant = valeurStock();
        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(50.00));

        assertThat(valeurStock().subtract(avant)).isEqualByComparingTo("-250.00");
        assertThat(ligneStock(achat).getCoutUnitaire()).isEqualTo(50.0);
    }

    /** Consumed down to nothing, there is no stock left to carry a correction. */
    @Test
    void nothingIsRevaluedOnceTheDeliveryIsFullyConsumed() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");
        consommer(achat, 10);

        BigDecimal avant = valeurStock();
        var result = achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        assertThat(valeurStock()).isEqualByComparingTo(avant);
        assertThat(result.warning()).contains("plus en stock");
    }

    /** A part-consumed correction is worth saying out loud. */
    @Test
    void aPartialRevaluationIsReported() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");
        consommer(achat, 5);

        var result = achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        assertThat(result.warning()).contains("consommée");
    }

    /**
     * A line holding more than this delivery brought — a second, dearer order
     * of the same article — must be corrected for this delivery only, not
     * spread thin across everything on the line.
     */
    @Test
    void aSecondDeliveryOnTheSameLineIsNotRepricedByTheFirst() {
        AchatResponse premier = createAchat(10, 100.00);
        achatService.validateBL(premier.id(), "BL-1");
        BigDecimal avant = valeurStock();

        achatService.updateLignePrix(
                premier.id(), premier.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        // only the 10 units this order delivered move, by 50 each
        assertThat(valeurStock().subtract(avant)).isEqualByComparingTo("500.00");
    }

    /** Re-pricing there and back leaves the valuation exactly where it started. */
    @Test
    void repricingThereAndBackIsANoOp() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");
        BigDecimal avant = valeurStock();

        UUID ligneId = achat.lignes().getFirst().id();
        achatService.updateLignePrix(achat.id(), ligneId, new UpdateLignePrixRequest(150.00));
        achatService.updateLignePrix(achat.id(), ligneId, new UpdateLignePrixRequest(100.00));

        assertThat(valeurStock()).isEqualByComparingTo(avant);
    }

    /** Material already posé is still on the balance sheet, so it follows too. */
    @Test
    void materialAlreadyPosedIsRevaluedWithTheRest() {
        AchatResponse achat = createAchat(10, 100.00);
        achatService.validateBL(achat.id(), "BL");
        stockService.affecterAuxTravaux(
                new com.buildflow.erp.domain.stock.dto.request.AffecterTravauxRequest(
                        articleIdOf(achat), chantierIdOf(achat), new BigDecimal("10.000"), "pose"));

        BigDecimal avant = valeurStock();
        achatService.updateLignePrix(
                achat.id(), achat.lignes().getFirst().id(), new UpdateLignePrixRequest(150.00));

        assertThat(valeurStock().subtract(avant)).isEqualByComparingTo("500.00");
    }

    // ── Re-pricing a whole commande ───────────────────────────────────

    /** The order total is set directly; the lines keep their proportions. */
    @Test
    void theWholeOrderCanBeRepricedToANewTotal() {
        AchatResponse achat = createAchat(10, 100.00);          // 1000 HT

        AchatResponse updated = achatService.updateMontantHt(
                achat.id(), new BigDecimal("1500.00")).achat();

        assertThat(updated.ht()).isEqualByComparingTo("1500.00");
        assertThat(updated.ttc()).isEqualByComparingTo("1650.00");
        assertThat(updated.lignes().getFirst().prixUnitaire()).isEqualTo(150.0);
    }

    /** Re-pricing the order carries the same corrections a line re-price does. */
    @Test
    void repricingTheWholeOrderRevaluesStockAndReconcilesTheCaisse() {
        AchatResponse achat = createAchat(10, 100.00);
        UUID caisseId = caisseRepository.findByChantierId(chantierIdOf(achat)).getFirst().getId();
        tresorerieService.enregistrerTransaction(caisseId, new CreateTransactionRequest(
                TypeTransaction.CREDIT, new BigDecimal("5000.00"), "Appro", null, null, null, null));

        achatService.validateBL(achat.id(), "BL");
        achatService.validateFacture(achat.id(), "FA");
        achatService.validatePaiement(achat.id(), ModePaiement.CAISSE);
        BigDecimal stockApresPaiement = valeurStock();

        achatService.updateMontantHt(achat.id(), new BigDecimal("1500.00"));

        assertThat(valeurStock().subtract(stockApresPaiement))
                .as("stock follows the new price")
                .isEqualByComparingTo("500.00");
        assertThat(caisseRepository.findById(caisseId).orElseThrow().getSolde())
                .as("5000 - 1650 TTC")
                .isEqualByComparingTo("3350.00");
    }

    /** With nothing to keep in proportion, say so rather than dividing by zero. */
    @Test
    void anOrderAtZeroCannotBeRepricedByTotal() {
        AchatResponse achat = createAchat(10, 0.00);

        assertThatThrownBy(() -> achatService.updateMontantHt(achat.id(), new BigDecimal("1000.00")))
                .hasMessageContaining("ligne par ligne");
    }

    /** A negative total is not a price. */
    @Test
    void anOrderTotalCannotBeNegative() {
        AchatResponse achat = createAchat(10, 100.00);

        assertThatThrownBy(() -> achatService.updateMontantHt(achat.id(), new BigDecimal("-1.00")))
                .hasMessageContaining("positif");
    }

    /** The user is told when the correction had nowhere to land. */
    @Test
    void aWarningIsRaisedWhenTheStockIsGone() {
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.LIVRE, AchatService.Revalorisation.IMPOSSIBLE))
                .contains("plus en stock");
        assertThat(AchatServiceImpl.repricingWarning(AchatStatut.PAYE, AchatService.Revalorisation.IMPOSSIBLE))
                .contains("caisse")
                .contains("plus en stock");
    }

    private BigDecimal valeurStock() {
        return BigDecimal.valueOf(stockArticleRepository.sumValeurStockHt())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Takes quantity out of the stock the order delivered, as site use would. */
    private void consommer(AchatResponse achat, double quantite) {
        stockService.createMouvement(new com.buildflow.erp.domain.stock.dto.request.CreateMouvementStockRequest(
                articleIdOf(achat), chantierIdOf(achat),
                com.buildflow.erp.domain.stock.entity.TypeMouvement.SORTIE,
                BigDecimal.valueOf(quantite).setScale(3, java.math.RoundingMode.HALF_UP),
                "consommation", null));
    }

    private com.buildflow.erp.domain.stock.entity.StockArticle ligneStock(AchatResponse achat) {
        return stockArticleRepository
                .findByArticleIdAndChantierId(articleIdOf(achat), chantierIdOf(achat))
                .orElseThrow();
    }

    private UUID articleIdOf(AchatResponse achat) {
        return articleRepository.findByCode(achat.lignes().getFirst().articleCode())
                .orElseThrow().getId();
    }

    private UUID chantierIdOf(AchatResponse achat) {
        return chantierService.findAll().stream()
                .filter(c -> c.nom().equals(achat.chantierNom()))
                .findFirst().orElseThrow().id();
    }

    // ── Fixtures ──────────────────────────────────────────────────────

    private AchatResponse createAchat(double quantite, double prixUnitaire) {
        return createAchat(quantite, prixUnitaire, prixUnitaire);
    }

    /** Une commande de deux lignes portant chacune son propre taux. */
    private AchatResponse createAchatDeuxTaux(double qte1, double pu1, BigDecimal tva1,
                                              double qte2, double pu2, BigDecimal tva2) {
        String suffix = "IT-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        UUID chantierId = chantierService.create(new CreateChantierRequest(
                "Chantier taux " + suffix, "Client", "adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(3),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();

        Fournisseur fournisseur = new Fournisseur();
        fournisseur.setCode("F-" + suffix);
        fournisseur.setRaisonSociale("Fournisseur " + suffix);
        fournisseur.setIce("ICE" + suffix);
        fournisseur.setStatut(FournisseurStatut.ACTIF);
        fournisseur = fournisseurRepository.save(fournisseur);

        UUID a1 = article(suffix + "-A", pu1, tva1);
        UUID a2 = article(suffix + "-B", pu2, tva2);

        return achatService.create(new CreateAchatRequest(
                fournisseur.getId(), chantierId, LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(a1, qte1, pu1, null),
                        new CreateLigneAchatRequest(a2, qte2, pu2, null)),
                null, null));
    }

    private UUID article(String suffix, double prix, BigDecimal tvaPourcent) {
        CategorieArticle categorie = categorieArticleRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategorieArticle c = new CategorieArticle();
                    c.setCode("ITT" + (SEQ.get() % 1000));
                    c.setLibelle("Catégorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-T-" + suffix);
        article.setDesignation("Article " + suffix);
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(prix);
        article.setTvaRate(tvaPourcent);
        return articleRepository.save(article).getId();
    }

    /** prixAchatRef is the article's catalogue price; prixUnitaire is what this order pays. */
    private AchatResponse createAchat(double quantite, double prixUnitaire, double prixAchatRef) {
        return createAchat(quantite, prixUnitaire, prixAchatRef, new BigDecimal("10.00"));
    }

    /** {@code tvaPourcent} est le taux porté par l'article, en pourcentage. */
    private AchatResponse createAchat(double quantite, double prixUnitaire, double prixAchatRef,
                                      BigDecimal tvaPourcent) {
        String suffix = "IT-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

        UUID chantierId = chantierService.create(new CreateChantierRequest(
                "Chantier prix " + suffix, "Client", "adresse", "Casablanca",
                ChantierStatut.EN_PREPARATION, LocalDate.now(), LocalDate.now().plusMonths(3),
                new BigDecimal("100000.00"), "Chef", List.of(), List.of())).id();

        Fournisseur fournisseur = new Fournisseur();
        fournisseur.setCode("F-" + suffix);
        fournisseur.setRaisonSociale("Fournisseur " + suffix);
        fournisseur.setIce("ICE" + suffix);
        fournisseur.setStatut(FournisseurStatut.ACTIF);
        fournisseur = fournisseurRepository.save(fournisseur);

        CategorieArticle categorie = categorieArticleRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategorieArticle c = new CategorieArticle();
                    c.setCode("ITP" + (SEQ.get() % 1000));
                    c.setLibelle("Catégorie de test");
                    return categorieArticleRepository.save(c);
                });

        Article article = new Article();
        article.setCode("ART-P-" + suffix);
        article.setDesignation("Article de test");
        article.setCategorie(categorie);
        article.setUnite("U");
        article.setPrixAchatRef(prixAchatRef);
        article.setTvaRate(tvaPourcent);
        article = articleRepository.save(article);

        return achatService.create(new CreateAchatRequest(
                fournisseur.getId(), chantierId, LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(new CreateLigneAchatRequest(article.getId(), quantite, prixUnitaire, null)),
                null, null));
    }
}
