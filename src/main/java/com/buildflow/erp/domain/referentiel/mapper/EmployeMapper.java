package com.buildflow.erp.domain.referentiel.mapper;

import com.buildflow.erp.domain.referentiel.dto.request.CreateEmployeRequest;
import com.buildflow.erp.domain.referentiel.dto.response.EmployeResponse;
import com.buildflow.erp.domain.referentiel.entity.Employe;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeMapper {

    @Mapping(target = "chantierActuelId", source = "chantierActuel.id")
    @Mapping(target = "chantierActuelNom", source = "chantierActuel.nom")
    EmployeResponse toResponse(Employe employe);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "chantierActuel", ignore = true) // Handled in service
    Employe toEntity(CreateEmployeRequest request);
}