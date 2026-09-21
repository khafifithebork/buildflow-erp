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

    /**
     * Les mêmes règlements, lus hors taxes.
     *
     * <p>Un paiement ne porte qu'un montant TTC ; c'est le contrat qui détaille
     * HT, TVA et TTC. La part hors taxes se calcule donc au prorata du ratio
     * propre à chaque contrat, plutôt qu'en appliquant un taux unique — deux
     * contrats peuvent porter des TVA différentes. Même méthode que
     * {@code ContratSousTraitantRepository.sumResteAPayerHt}.
     *
     * <p>Les contrats à montantTtc nul sont écartés : la division n'aurait pas
     * de sens, et il n'y a rien à ventiler.
     */
    @Query("""
            SELECT COALESCE(SUM(p.montant * p.contrat.montantHt / p.contrat.montantTtc), 0)
            FROM PaiementSousTraitant p
            WHERE p.statut = com.buildflow.erp.domain.soustraitance.entity.PaiementStatut.PAYE
            AND (p.modePaiement IS NULL OR p.modePaiement <> com.buildflow.erp.common.paiement.ModePaiement.CAISSE)
            AND p.datePaiement BETWEEN :start AND :end
            AND p.contrat.montantTtc > 0
            """)
    BigDecimal sumPayeesHtBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
