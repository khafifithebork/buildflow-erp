package com.buildflow.erp.domain.salaires.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A payroll disbursement that has no payslip behind it.
 *
 * <p>{@link FichePaie} starts from an employee and a month and derives the net.
 * A demande carries a net that is already known, so the amount is an input here
 * and the employee is absent entirely. Chantier and BPU line stay optional: a
 * demande is not always imputable at the moment it is raised.
 */
@Entity
@Table(name = "demandes_paie")
@Getter
@Setter
@NoArgsConstructor
public class DemandePaie extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @Column(nullable = false)
    private String libelle;

    /** Format: "2026-07" */
    @Column(nullable = false, length = 7)
    private String periode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chantier_id")
    private Chantier chantier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bpu_ligne_id")
    private BpuLigne bpuLigne;

    /** Entered by the requester, never recomputed. */
    @Column(name = "montant_net", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantNet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemandePaieStatut statut = DemandePaieStatut.SOUMISE;

    /** Null until the demande is actually paid; the payer chooses explicitly. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", length = 20)
    private ModePaiement modePaiement;
}
