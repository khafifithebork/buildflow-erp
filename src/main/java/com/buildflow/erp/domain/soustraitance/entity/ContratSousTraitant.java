package com.buildflow.erp.domain.soustraitance.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.SousTraitant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contrats_sous_traitant")
@Getter
@Setter
@NoArgsConstructor
public class ContratSousTraitant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sous_traitant_id", nullable = false)
    private SousTraitant sousTraitant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;

    @Column(nullable = false)
    private String objet;

    @Column(name = "montant_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantHt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal tva = BigDecimal.ZERO;

    @Column(name = "montant_ttc", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTtc;

    @Column(name = "montant_paye", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantPaye = BigDecimal.ZERO;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContratStatut statut = ContratStatut.EN_COURS;

    @OneToMany(mappedBy = "contrat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaiementSousTraitant> paiements = new ArrayList<>();
}
