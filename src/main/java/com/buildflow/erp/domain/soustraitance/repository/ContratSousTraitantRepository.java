package com.buildflow.erp.domain.soustraitance.repository;

import com.buildflow.erp.domain.soustraitance.entity.ContratSousTraitant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ContratSousTraitantRepository extends JpaRepository<ContratSousTraitant, UUID> {
    boolean existsByReference(String reference);
    List<ContratSousTraitant> findByChantierId(UUID chantierId);
    List<ContratSousTraitant> findBySousTraitantId(UUID sousTraitantId);

    // NOTE: the backend has no "travaux réalisés" (validated field work) tracking yet,
    // so the full contracted HT amount is treated as "engaged" spend once a contract
    // is imputed to a BPU line, excluding cancelled (RESILIE) contracts.
    @Query("""
            SELECT COALESCE(SUM(c.montantHt), 0) FROM ContratSousTraitant c
            WHERE c.bpuLigne.id = :bpuLigneId
            AND c.statut <> com.buildflow.erp.domain.soustraitance.entity.ContratStatut.RESILIE
            """)
    BigDecimal sumMontantEngageByBpuLigneId(@Param("bpuLigneId") UUID bpuLigneId);

    // Dettes sous-traitants: outstanding balance across all contracts, as of now.
    @Query("""
            SELECT COALESCE(SUM(c.montantTtc - c.montantPaye), 0) FROM ContratSousTraitant c
            WHERE c.montantPaye < c.montantTtc
            """)
    BigDecimal sumResteAPayer();
}
