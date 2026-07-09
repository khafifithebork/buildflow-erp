package com.buildflow.erp.domain.salaires.mapper;

import com.buildflow.erp.domain.salaires.dto.response.FichePaieResponse;
import com.buildflow.erp.domain.salaires.entity.FichePaie;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FichePaieMapper {

    @Mapping(target = "employeId", source = "employe.id")
    @Mapping(target = "employeNomComplet", expression = "java(fichePaie.getEmploye().getPrenom() + \" \" + fichePaie.getEmploye().getNom())")
    @Mapping(target = "employeMatricule", source = "employe.matricule")
    @Mapping(target = "chantierId", source = "chantier.id")
    @Mapping(target = "chantierNom", source = "chantier.nom")
    FichePaieResponse toResponse(FichePaie fichePaie);
}
