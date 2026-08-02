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
}
