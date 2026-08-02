package com.buildflow.erp.domain.attachement.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attachements")
@Getter
@Setter
@NoArgsConstructor
public class Attachement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;

    @Column(nullable = false, length = 50)
    private String reference;

    @Column(name = "date_attachement", nullable = false)
    private LocalDate dateAttachement;

    @Column(name = "montant_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantHt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal tva;

    @Column(name = "montant_ttc", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTtc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttachementStatut statut = AttachementStatut.SOUMIS;

    @Column(name = "date_encaissement")
    private LocalDateTime dateEncaissement;

    @OneToMany(mappedBy = "attachement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttachementLigne> lignes = new ArrayList<>();
}
