package com.buildflow.erp.domain.achats.repository;

import com.buildflow.erp.domain.achats.entity.PaiementAchat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaiementAchatRepository extends JpaRepository<PaiementAchat, UUID> {

    List<PaiementAchat> findByAchatIdOrderByDatePaiementDesc(UUID achatId);

    void deleteByAchatId(UUID achatId);

    /**
     * Les reglements sortis sur la periode, hors caisse.
     *
     * <p>La caisse est ecartee pour la meme raison que partout ailleurs : ses
     * sorties sont deja portees par les ecritures de caisse, les compter ici
     * les compterait deux fois.
     */
    @Query("""
            SELECT COALESCE(SUM(p.montant), 0) FROM PaiementAchat p
            WHERE (p.modePaiement IS NULL OR p.modePaiement <> com.buildflow.erp.common.paiement.ModePaiement.CAISSE)
            AND p.datePaiement BETWEEN :start AND :end
            """)
    BigDecimal sumPayeesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Les memes reglements, lus hors taxes.
     *
     * <p>Un reglement ne porte qu'un montant TTC ; c'est la commande qui
     * detaille HT et TTC. La part hors taxes se calcule donc au prorata du
     * ratio propre a chaque commande, deux commandes pouvant porter des TVA
     * differentes.
     */
    @Query("""
            SELECT COALESCE(SUM(p.montant * p.achat.ht / p.achat.ttc), 0) FROM PaiementAchat p
            WHERE (p.modePaiement IS NULL OR p.modePaiement <> com.buildflow.erp.common.paiement.ModePaiement.CAISSE)
            AND p.datePaiement BETWEEN :start AND :end
            AND p.achat.ttc > 0
            """)
    BigDecimal sumPayeesHtBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Idem, restreint aux commandes a effet chantier et sans effet fiscal. */
    @Query("""
            SELECT COALESCE(SUM(p.montant * p.achat.ht / p.achat.ttc), 0) FROM PaiementAchat p
            WHERE (p.modePaiement IS NULL OR p.modePaiement <> com.buildflow.erp.common.paiement.ModePaiement.CAISSE)
            AND p.datePaiement BETWEEN :start AND :end
            AND p.achat.ttc > 0
            AND p.achat.impactAnalytiqueChantier = true
            AND p.achat.impactComptableFiscal = false
            """)
    BigDecimal sumPayeesHtEffetChantierBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
