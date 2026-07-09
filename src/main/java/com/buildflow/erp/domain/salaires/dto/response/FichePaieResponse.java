package com.buildflow.erp.domain.salaires.dto.response;

import com.buildflow.erp.domain.salaires.entity.FichePaieStatut;

import java.math.BigDecimal;
import java.util.UUID;

public record FichePaieResponse(
        UUID id,
        String reference,
        UUID employeId,
        String employeNomComplet,
        String employeMatricule,
        UUID chantierId,
        String chantierNom,
        String periode,
        int joursTravailles,
        BigDecimal salaireBase,
        BigDecimal heuresSupplementaires,
        BigDecimal montantHeuresSupp,
        BigDecimal primeTransport,
        BigDecimal primePanier,
        BigDecimal autresPrimes,
        BigDecimal avance,
        BigDecimal deductionsCnss,
        BigDecimal deductionsIr,
        BigDecimal netAPayer,
        FichePaieStatut statut
) {}
