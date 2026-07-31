package com.buildflow.erp.domain.tresorerie.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "caisses")
@Getter
@Setter
@NoArgsConstructor
public class Caisse extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal solde = BigDecimal.ZERO;

    @Column(name = "seuil_minimum", nullable = false, precision = 15, scale = 2)
    private BigDecimal seuilMinimum = BigDecimal.ZERO;

    @OneToMany(mappedBy = "caisse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaisseTransaction> transactions = new ArrayList<>();
}
