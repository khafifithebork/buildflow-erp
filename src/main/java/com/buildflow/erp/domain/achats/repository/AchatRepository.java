package com.buildflow.erp.domain.achats.repository;

import com.buildflow.erp.domain.achats.entity.Achat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    // Le pendant de la dette : ce qui a déjà été réglé, toutes commandes
    // soldées confondues. Aucun filtre sur le mode de paiement ni sur la
    // période — la carte lit un cumul, pas un flux, et dette + déjà payé doit
    // redonner le total commandé.
    @Query("""
            SELECT COALESCE(SUM(a.ttc), 0) FROM Achat a
            WHERE a.statut = com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            """)
    BigDecimal sumTtcPayees();

    // La dette par fournisseur, même définition que sumTtcNonPayees mais
    // ventilée : les valeurs rendues ici somment exactement au total affiché
    // sur le tableau de bord.
    //
    // Une seule requête groupée plutôt qu'un appel par fournisseur — la liste
    // en compte une centaine et le N+1 se paierait à chaque chargement de page.
    @Query("""
            SELECT a.fournisseur.id, COALESCE(SUM(a.ttc), 0) FROM Achat a
            WHERE a.statut <> com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            GROUP BY a.fournisseur.id
            """)
    List<Object[]> sumTtcNonPayeesParFournisseur();

    @Query("""
            SELECT COALESCE(SUM(a.ttc), 0) FROM Achat a
            WHERE a.fournisseur.id = :fournisseurId
            AND a.statut <> com.buildflow.erp.domain.achats.entity.AchatStatut.PAYE
            """)
    BigDecimal sumTtcNonPayeesByFournisseurId(@Param("fournisseurId") UUID fournisseurId);

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