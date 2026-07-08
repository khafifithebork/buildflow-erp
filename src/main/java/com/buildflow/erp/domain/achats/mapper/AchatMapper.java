package com.buildflow.erp.domain.achats.mapper;

import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.entity.Achat;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {LigneAchatMapper.class})
public interface AchatMapper {

    @Mapping(target = "fournisseurNom", source = "fournisseur.raisonSociale")
    @Mapping(target = "chantierNom", source = "chantier.nom")
    @Mapping(target = "status", source = "statut") // Map entity 'statut' to DTO 'status'
    AchatResponse toResponse(Achat achat);
}