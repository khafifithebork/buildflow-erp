package com.buildflow.erp.domain.tresorerie.mapper;

import com.buildflow.erp.domain.tresorerie.dto.response.CaisseResponse;
import com.buildflow.erp.domain.tresorerie.dto.response.CaisseTransactionResponse;
import com.buildflow.erp.domain.tresorerie.entity.Caisse;
import com.buildflow.erp.domain.tresorerie.entity.CaisseTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CaisseMapper {

    @Mapping(target = "chantierId", source = "chantier.id")
    @Mapping(target = "chantierNom", source = "chantier.nom")
    @Mapping(target = "enAlerte", expression = "java(isEnAlerte(caisse))")
    @Mapping(target = "dernieresTransactions", source = "transactions")
    CaisseResponse toResponse(Caisse caisse);

    @Mapping(target = "bpuLigneRef", source = "bpuLigne.ref")
    CaisseTransactionResponse toTransactionResponse(CaisseTransaction transaction);

    List<CaisseTransactionResponse> toTransactionResponseList(List<CaisseTransaction> transactions);

    default boolean isEnAlerte(Caisse caisse) {
        return caisse.getSeuilMinimum().compareTo(BigDecimal.ZERO) > 0
                && caisse.getSolde().compareTo(caisse.getSeuilMinimum()) <= 0;
    }
}
