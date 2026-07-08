package com.buildflow.erp.domain.tresorerie.dto.response;

import com.buildflow.erp.domain.tresorerie.entity.TypeTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaisseTransactionResponse(
        UUID id,
        TypeTransaction typeTransaction,
        BigDecimal montant,
        String motif,
        String referenceDocument,
        LocalDateTime createdAt
) {}
