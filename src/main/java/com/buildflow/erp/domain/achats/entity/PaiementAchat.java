package com.buildflow.erp.domain.achats.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.common.paiement.ModePaiement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un reglement porte sur une commande fournisseur.
 *
 * <p>Le cumul regle vit sur {@code Achat.montantPaye} et repond a « combien
 * reste-t-il du ». Ce registre repond a l'autre question : quand est sorti
 * l'argent. Les decaissements du tableau de bord sont bornes par periode, et
 * une colonne cumulative ne porte aucune date.
 *
 * <p>Pas de statut ici, contrairement a {@code PaiementSousTraitant} : un
 * paiement sous-traitant se demande puis s'approuve, alors qu'une ligne ecrite
 * ici constate un reglement deja fait.
 */
@Entity
@Table(name = "paiements_achat")
@Getter
@Setter
@NoArgsConstructor
public class PaiementAchat extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achat_id", nullable = false)
    private Achat achat;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    /** La date qui classe ce reglement dans une periode de decaissement. */
    @Column(name = "date_paiement", nullable = false)
    private LocalDate datePaiement;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", length = 20)
    private ModePaiement modePaiement;
}
