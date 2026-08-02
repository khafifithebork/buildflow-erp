package com.buildflow.erp.domain.salaires.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.Employe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "fiches_paie",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employe_id", "periode"}))
@Getter
@Setter
@NoArgsConstructor
public class FichePaie extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;

    /** Format: "2026-07" */
    @Column(nullable = false, length = 7)
    private String periode;

    @Column(name = "jours_travailles", nullable = false)
    private int joursTravailles;

    @Column(name = "salaire_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal salaireBase;

    @Column(name = "heures_supplementaires", nullable = false, precision = 10, scale = 2)
    private BigDecimal heuresSupplementaires = BigDecimal.ZERO;

    @Column(name = "montant_heures_supp", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantHeuresSupp = BigDecimal.ZERO;

    @Column(name = "prime_transport", nullable = false, precision = 15, scale = 2)
    private BigDecimal primeTransport = BigDecimal.ZERO;

    @Column(name = "prime_panier", nullable = false, precision = 15, scale = 2)
    private BigDecimal primePanier = BigDecimal.ZERO;

    @Column(name = "autres_primes", nullable = false, precision = 15, scale = 2)
    private BigDecimal autresPrimes = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal avance = BigDecimal.ZERO;

    @Column(name = "deductions_cnss", nullable = false, precision = 15, scale = 2)
    private BigDecimal deductionsCnss = BigDecimal.ZERO;

    @Column(name = "deductions_ir", nullable = false, precision = 15, scale = 2)
    private BigDecimal deductionsIr = BigDecimal.ZERO;

    @Column(name = "net_a_payer", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAPayer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FichePaieStatut statut = FichePaieStatut.BROUILLON;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", nullable = false, length = 20)
    private ModePaiement modePaiement = ModePaiement.CAISSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bpu_ligne_id")
    private BpuLigne bpuLigne;
}
