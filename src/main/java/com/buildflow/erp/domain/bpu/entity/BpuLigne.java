package com.buildflow.erp.domain.bpu.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "bpu_lignes")
@Getter
@Setter
@NoArgsConstructor
public class BpuLigne extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;

    @Column(nullable = false, length = 50)
    private String ref;

    @Column(nullable = false)
    private String designation;

    @Column(nullable = false, length = 20)
    private String unite;

    @Column(name = "qte_prevue", nullable = false, precision = 15, scale = 3)
    private BigDecimal qtePrevue;

    // DOUBLE PRECISION: a unit price may carry more than two decimals. The
    // budget below stays BigDecimal — that is the figure that gets committed.
    @Column(name = "pu_ht", nullable = false)
    private double puHt;

    @Column(name = "budget_prevu_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal budgetPrevuHt;
}
