package com.buildflow.erp.domain.comptabilite.service;

import com.buildflow.erp.domain.comptabilite.dto.response.EcritureComptableResponse;

import java.util.List;

public interface ComptabiliteService {
    List<EcritureComptableResponse> listEcritures();
}