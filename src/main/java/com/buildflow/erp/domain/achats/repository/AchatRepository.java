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

    // No explicit "date paiement" field on Achat — dateCommande is used as the
    // best-available proxy for which period a paid order's outflow falls in.
    @Query("""
            SELECT COALESCE(SUM(a.ttc), 0) FROM Achat a
            WHERE a.statut = com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            AND a.dateCommande BETWEEN :start AND :end
            """)
    BigDecimal sumTtcPayeesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}