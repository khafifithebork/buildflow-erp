package com.buildflow.erp.domain.attachement.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "attachement_lignes")
@Getter
@Setter
@NoArgsConstructor
public class AttachementLigne extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachement_id", nullable = false)
    @JsonIgnore
    private Attachement attachement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bpu_ligne_id", nullable = false)
    private BpuLigne bpuLigne;

    @Column(name = "ancien_cumul", nullable = false, precision = 15, scale = 3)
    private BigDecimal ancienCumul;

    @Column(name = "nouveau_cumul", nullable = false, precision = 15, scale = 3)
    private BigDecimal nouveauCumul;

    @Column(name = "montant_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantHt;
}
