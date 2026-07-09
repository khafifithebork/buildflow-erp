package com.buildflow.erp.domain.soustraitance.mapper;

import com.buildflow.erp.domain.soustraitance.dto.response.ContratSousTraitantResponse;
import com.buildflow.erp.domain.soustraitance.dto.response.PaiementSousTraitantResponse;
import com.buildflow.erp.domain.soustraitance.entity.ContratSousTraitant;
import com.buildflow.erp.domain.soustraitance.entity.PaiementSousTraitant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SousTraitanceMapper {

    @Mapping(target = "sousTraitantId", source = "sousTraitant.id")
    @Mapping(target = "sousTraitantRaisonSociale", source = "sousTraitant.raisonSociale")
    @Mapping(target = "chantierId", source = "chantier.id")
    @Mapping(target = "chantierNom", source = "chantier.nom")
    @Mapping(target = "resteAPayer", expression = "java(contrat.getMontantTtc().subtract(contrat.getMontantPaye()))")
    ContratSousTraitantResponse toContratResponse(ContratSousTraitant contrat);

    PaiementSousTraitantResponse toPaiementResponse(PaiementSousTraitant paiement);
}
