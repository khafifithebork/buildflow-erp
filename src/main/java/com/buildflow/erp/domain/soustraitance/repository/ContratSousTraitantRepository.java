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
    long countByChantierId(UUID chantierId);
    List<ContratSousTraitant> findBySousTraitantId(UUID sousTraitantId);

    // Seuil commun aux quatre sources du bordereau : l'engagement compte des que
    // le document existe, pas au paiement. Un contrat signe engage son montant,
    // les contrats resilies exceptes.
    //
    // NOTE: the backend has no "travaux réalisés" (validated field work) tracking yet,
    // so the full contracted HT amount is treated as "engaged" spend.
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

    // Ce qui a déjà été versé, tous contrats confondus. montantPaye porte
    // le cumul TTC des règlements, y compris sur les contrats soldés.
    @Query("""
            SELECT COALESCE(SUM(c.montantPaye), 0) FROM ContratSousTraitant c
            """)
    BigDecimal sumMontantPayeTtc();

    // Outstanding balance valued HT. montantPaye is a TTC figure, so the
    // remainder is prorated by each contract's own HT/TTC ratio rather than
    // assuming one rate — contracts can carry different TVA amounts.
    @Query("""
            SELECT COALESCE(SUM((c.montantTtc - c.montantPaye) * c.montantHt / c.montantTtc), 0)
            FROM ContratSousTraitant c
            WHERE c.montantPaye < c.montantTtc AND c.montantTtc > 0
            """)
    BigDecimal sumResteAPayerHt();
}
