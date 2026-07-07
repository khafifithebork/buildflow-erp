package com.buildflow.erp.domain.referentiel.mapper;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ChantierResponse;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChantierMapper {
    ChantierResponse toResponse(Chantier chantier);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "jalons", ignore = true) // Handled manually to set parent FK
    @Mapping(target = "depensesHt", ignore = true)
    @Mapping(target = "avancement", ignore = true)
    @Mapping(target = "nombreOuvriers", ignore = true)
    Chantier toEntity(CreateChantierRequest request);
}