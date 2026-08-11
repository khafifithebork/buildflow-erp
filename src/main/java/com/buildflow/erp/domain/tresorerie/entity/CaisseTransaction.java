package com.buildflow.erp.domain.tresorerie.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "caisse_transactions")
@Getter
@Setter
@NoArgsConstructor
public class CaisseTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caisse_id", nullable = false)
    private Caisse caisse;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_transaction", nullable = false, length = 20)
    private TypeTransaction typeTransaction;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private String motif;

    @Column(name = "reference_document", length = 100)
    private String referenceDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bpu_ligne_id")
    private BpuLigne bpuLigne;

    /**
     * True when this movement corrects an earlier one rather than being a
     * movement in its own right — a refund after a settled order was re-priced,
     * for instance. Décaissements net these out; ordinary credits such as
     * funding the caisse are not netted, because they are money coming in.
     */
    @Column(nullable = false)
    private boolean ajustement = false;

    /** "L'achat a-t-il réellement servi au chantier ?" — analytic (site cost) impact. */
    @Column(name = "impact_analytique_chantier", nullable = false)
    private boolean impactAnalytiqueChantier = false;

    /** "Y a-t-il une facture officielle à déclarer ?" — accounting/tax impact. */
    @Column(name = "impact_comptable_fiscal", nullable = false)
    private boolean impactComptableFiscal = false;
}
