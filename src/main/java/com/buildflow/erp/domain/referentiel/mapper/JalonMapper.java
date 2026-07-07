package com.buildflow.erp.domain.referentiel.mapper;
import com.buildflow.erp.domain.referentiel.dto.request.CreateJalonRequest;
import com.buildflow.erp.domain.referentiel.dto.response.JalonResponse;
import com.buildflow.erp.domain.referentiel.entity.Jalon;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface JalonMapper {
    JalonResponse toResponse(Jalon jalon);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "chantier", ignore = true) // Parent reference set manually in service
    Jalon toEntity(CreateJalonRequest request);
}