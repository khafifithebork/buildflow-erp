package com.buildflow.erp.domain.achats.repository;

import com.buildflow.erp.domain.achats.entity.Achat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface AchatRepository extends JpaRepository<Achat, UUID> {
    boolean existsByRef(String ref);

    long countByChantierId(UUID chantierId);

    @Query("""
            SELECT COALESCE(SUM(l.total), 0) FROM LigneAchat l
            WHERE l.bpuLigne.id = :bpuLigneId
            AND l.achat.statut IN ('LIVRE', 'FACTURE', 'PAYE')
            """)
    BigDecimal sumMontantEngageByBpuLigneId(@Param("bpuLigneId") UUID bpuLigneId);

    // Dettes fournisseurs: unpaid orders, as of now (not period-scoped).
    @Query("""
            SELECT COALESCE(SUM(a.ttc), 0) FROM Achat a
            WHERE a.statut <> com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            """)
    BigDecimal sumTtcNonPayees();

    // Same outstanding orders valued HT, for the margin formulas that read
    // everything net of tax.
    @Query("""
            SELECT COALESCE(SUM(a.ht), 0) FROM Achat a
            WHERE a.statut <> com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            """)
    BigDecimal sumHtNonPayees();

    // No explicit "date paiement" field on Achat — dateCommande is used as the
    // best-available proxy for which period a paid order's outflow falls in.
    @Query("""
            SELECT COALESCE(SUM(a.ttc), 0) FROM Achat a
            WHERE a.statut = com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            AND (a.modePaiement IS NULL OR a.modePaiement <> com.buildflow.erp.common.paiement.ModePaiement.CAISSE)
            AND a.dateCommande BETWEEN :start AND :end
            """)
    BigDecimal sumTtcPayeesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // Same set of paid orders, valued HT. Achats are the only outflow that
    // carries a separable TVA, so this is what makes a tax-free reading of the
    // décaissements possible at all.
    @Query("""
            SELECT COALESCE(SUM(a.ht), 0) FROM Achat a
            WHERE a.statut = com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            AND (a.modePaiement IS NULL OR a.modePaiement <> com.buildflow.erp.common.paiement.ModePaiement.CAISSE)
            AND a.dateCommande BETWEEN :start AND :end
            """)
    BigDecimal sumHtPayeesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // Paid orders that count towards the hors-fiscalité result: the purchase
    // genuinely served the site, and there is no official invoice to declare.
    // An order carrying a fiscal effect drops out of the total entirely.
    @Query("""
            SELECT COALESCE(SUM(a.ht), 0) FROM Achat a
            WHERE a.statut = com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            AND a.impactAnalytiqueChantier = true
            AND a.impactComptableFiscal = false
            AND (a.modePaiement IS NULL OR a.modePaiement <> com.buildflow.erp.common.paiement.ModePaiement.CAISSE)
            AND a.dateCommande BETWEEN :start AND :end
            """)
    BigDecimal sumHtPayeesEffetChantierBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}