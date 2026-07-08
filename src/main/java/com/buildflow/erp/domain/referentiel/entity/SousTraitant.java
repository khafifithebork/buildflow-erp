package com.buildflow.erp.domain.referentiel.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "sous_traitants")
@Getter
@Setter
@NoArgsConstructor
public class SousTraitant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "raison_sociale", nullable = false)
    private String raisonSociale;

    @Column(nullable = false, unique = true, length = 50)
    private String ice;

    @Column(nullable = false, length = 100)
    private String specialite;

    @Column
    private String contact;

    @Column(length = 20)
    private String telephone;

    @Column
    private String email;

    @Column(length = 100)
    private String ville;

    @Column(columnDefinition = "TEXT")
    private String adresse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SousTraitantStatut statut = SousTraitantStatut.ACTIF;

    @Column(name = "nombre_contrats_actifs", nullable = false)
    private int nombreContratsActifs = 0;

    @Column(name = "montant_total_paye", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTotalPaye = BigDecimal.ZERO;
}