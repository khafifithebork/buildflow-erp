package com.buildflow.erp.domain.tresorerie.repository;

import com.buildflow.erp.domain.tresorerie.entity.CaisseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CaisseTransactionRepository extends JpaRepository<CaisseTransaction, UUID> {

    /** Les écritures encore vivantes qui règlent un document donné. */
    List<CaisseTransaction> findByDocumentIdAndAnnuleFalse(UUID documentId);
    List<CaisseTransaction> findByCaisseIdOrderByCreatedAtDesc(UUID caisseId);

    /** Cash operations booked against any caisse of a given chantier. */
    long countByCaisse_ChantierId(UUID chantierId);

    /**
     * Les espèces sorties pour une ligne du bordereau.
     *
     * <p>Seules les écritures saisies à la main portent une ligne BPU : un
     * règlement de document passe par {@code debiterPourDocument}, qui n'en
     * impute aucune. Il n'y a donc pas de double compte avec les achats.
     *
     * <p>Le montant est rendu tel quel. Il s'appelait {@code ...Ttc} et son
     * seul appelant le divisait par 1,20 : une dépense d'espèces sans facture
     * ne porte pas de TVA à retrancher.
     */
    @Query("""
            SELECT COALESCE(SUM(t.montant), 0) FROM CaisseTransaction t
            WHERE t.bpuLigne.id = :bpuLigneId
            AND t.typeTransaction = com.buildflow.erp.domain.tresorerie.entity.TypeTransaction.DEBIT
            """)
    BigDecimal sumMontantByBpuLigneId(@Param("bpuLigneId") UUID bpuLigneId);

    // Net cash out, not gross debits. A correcting credit — the refund posted
    // when a settled order is re-priced down — has to come back off the total,
    // or an order paid 1200 and refunded 600 still reports 1200 of spend.
    // Ordinary credits are excluded rather than subtracted: funding the caisse
    // is money coming in, not negative spend.
    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN t.typeTransaction = com.buildflow.erp.domain.tresorerie.entity.TypeTransaction.DEBIT
                     THEN t.montant ELSE -t.montant END), 0)
            FROM CaisseTransaction t
            WHERE (t.typeTransaction = com.buildflow.erp.domain.tresorerie.entity.TypeTransaction.DEBIT
                   OR t.ajustement = true)
            AND t.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumDebitsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Cash outflows that count towards the hors-fiscalité result: same rule as
    // achats — served the site, and no official invoice to declare.
    // Same netting as above, restricted to the effet-chantier operations.
    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN t.typeTransaction = com.buildflow.erp.domain.tresorerie.entity.TypeTransaction.DEBIT
                     THEN t.montant ELSE -t.montant END), 0)
            FROM CaisseTransaction t
            WHERE (t.typeTransaction = com.buildflow.erp.domain.tresorerie.entity.TypeTransaction.DEBIT
                   OR t.ajustement = true)
            AND t.impactAnalytiqueChantier = true
            AND t.impactComptableFiscal = false
            AND t.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumDebitsEffetChantierBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
