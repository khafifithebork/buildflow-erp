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
    List<CaisseTransaction> findByCaisseIdOrderByCreatedAtDesc(UUID caisseId);

    /** Cash operations booked against any caisse of a given chantier. */
    long countByCaisse_ChantierId(UUID chantierId);

    @Query("""
            SELECT COALESCE(SUM(t.montant), 0) FROM CaisseTransaction t
            WHERE t.bpuLigne.id = :bpuLigneId
            AND t.typeTransaction = com.buildflow.erp.domain.tresorerie.entity.TypeTransaction.DEBIT
            """)
    BigDecimal sumMontantTtcByBpuLigneId(@Param("bpuLigneId") UUID bpuLigneId);

    @Query("""
            SELECT COALESCE(SUM(t.montant), 0) FROM CaisseTransaction t
            WHERE t.typeTransaction = com.buildflow.erp.domain.tresorerie.entity.TypeTransaction.DEBIT
            AND t.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumDebitsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Cash outflows that count towards the hors-fiscalité result: same rule as
    // achats — served the site, and no official invoice to declare.
    @Query("""
            SELECT COALESCE(SUM(t.montant), 0) FROM CaisseTransaction t
            WHERE t.typeTransaction = com.buildflow.erp.domain.tresorerie.entity.TypeTransaction.DEBIT
            AND t.impactAnalytiqueChantier = true
            AND t.impactComptableFiscal = false
            AND t.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumDebitsEffetChantierBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
