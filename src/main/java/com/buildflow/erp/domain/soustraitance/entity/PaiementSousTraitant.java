package com.buildflow.erp.domain.soustraitance.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.common.paiement.ModePaiement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "paiements_sous_traitant")
@Getter
@Setter
@NoArgsConstructor
public class PaiementSousTraitant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrat_id", nullable = false)
    private ContratSousTraitant contrat;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaiementStatut statut = PaiementStatut.EN_ATTENTE;

    @Column(name = "date_paiement")
    private LocalDate datePaiement;

    /** How this payment was settled. Null until it reaches PAYE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", length = 20)
    private ModePaiement modePaiement;
}
