package com.buildflow.erp.domain.referentiel.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "jalons")
@Getter
@Setter
@NoArgsConstructor
public class Jalon extends BaseEntity {

    @Column(nullable = false)
    private String libelle;

    @Column(name = "date_prevue", nullable = false)
    private LocalDate datePrevue;

    @Column(name = "date_reelle")
    private LocalDate dateReelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JalonStatut statut = JalonStatut.A_FAIRE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chantier_id", nullable = false)
    @JsonIgnore // Prevents infinite recursion if entity is accidentally serialized
    private Chantier chantier;
}