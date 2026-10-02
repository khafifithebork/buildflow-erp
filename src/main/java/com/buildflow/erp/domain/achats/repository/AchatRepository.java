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
            SELECT COALESCE(SUM(a.ttc - a.montantPaye), 0) FROM Achat a
            WHERE a.montantPaye < a.ttc
            """)
    BigDecimal sumTtcNonPayees();

    // Le pendant de la dette : ce qui a déjà été réglé, toutes commandes
    // soldées confondues. Aucun filtre sur le mode de paiement ni sur la
    // période — la carte lit un cumul, pas un flux, et dette + déjà payé doit
    // redonner le total commandé.
    @Query("""
            SELECT COALESCE(SUM(a.montantPaye), 0) FROM Achat a
            """)
    BigDecimal sumTtcPayees();

    // La dette par fournisseur, même définition que sumTtcNonPayees mais
    // ventilée : les valeurs rendues ici somment exactement au total affiché
    // sur le tableau de bord.
    //
    // Une seule requête groupée plutôt qu'un appel par fournisseur — la liste
    // en compte une centaine et le N+1 se paierait à chaque chargement de page.
    @Query("""
            SELECT a.fournisseur.id, COALESCE(SUM(a.ttc - a.montantPaye), 0) FROM Achat a
            WHERE a.montantPaye < a.ttc
            GROUP BY a.fournisseur.id
            """)
    List<Object[]> sumTtcNonPayeesParFournisseur();

    @Query("""
            SELECT COALESCE(SUM(a.ttc - a.montantPaye), 0) FROM Achat a
            WHERE a.fournisseur.id = :fournisseurId
            AND a.montantPaye < a.ttc
            """)
    BigDecimal sumTtcNonPayeesByFournisseurId(@Param("fournisseurId") UUID fournisseurId);

    // Le volume d'affaires de l'année civile, par fournisseur.
    //
    // HT : c'est ce que les écrans annoncent — « Achats annuels HT », « HT
    // cumulé ». Tous statuts confondus, y compris EN_COURS : une commande
    // passée compte dans le volume même si elle n'est pas encore livrée. La
    // dépense réellement engagée, elle, se lit ailleurs (BPU) et la dette dans
    // sumTtcNonPayees.
    //
    // Borné par dates plutôt que par YEAR(dateCommande) : l'intervalle reste
    // utilisable par un index, la fonction non.
    @Query("""
            SELECT a.fournisseur.id, COALESCE(SUM(a.ht), 0) FROM Achat a
            WHERE a.dateCommande BETWEEN :debut AND :fin
            GROUP BY a.fournisseur.id
            """)
    List<Object[]> sumHtParFournisseurEntre(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            SELECT COALESCE(SUM(a.ht), 0) FROM Achat a
            WHERE a.fournisseur.id = :fournisseurId
            AND a.dateCommande BETWEEN :debut AND :fin
            """)
    BigDecimal sumHtByFournisseurIdEntre(@Param("fournisseurId") UUID fournisseurId,
                                         @Param("debut") LocalDate debut,
                                         @Param("fin") LocalDate fin);

    // Same outstanding orders valued HT, for the margin formulas that read
    // everything net of tax.
    @Query("""
            SELECT COALESCE(SUM((a.ttc - a.montantPaye) * a.ht / a.ttc), 0) FROM Achat a
            WHERE a.montantPaye < a.ttc AND a.ttc > 0
            """)
    BigDecimal sumHtNonPayees();
}