package com.buildflow.erp.domain.soustraitance.repository;

import com.buildflow.erp.domain.soustraitance.entity.PaiementSousTraitant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaiementSousTraitantRepository extends JpaRepository<PaiementSousTraitant, UUID> {
    boolean existsByReference(String reference);
    List<PaiementSousTraitant> findByContratIdOrderByCreatedAtDesc(UUID contratId);

    @Query("""
            SELECT COALESCE(SUM(p.montant), 0) FROM PaiementSousTraitant p
            WHERE p.statut = com.buildflow.erp.domain.soustraitance.entity.PaiementStatut.PAYE
            AND (p.modePaiement IS NULL OR p.modePaiement <> com.buildflow.erp.common.paiement.ModePaiement.CAISSE)
            AND p.datePaiement BETWEEN :start AND :end
            """)
    BigDecimal sumPayeesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
