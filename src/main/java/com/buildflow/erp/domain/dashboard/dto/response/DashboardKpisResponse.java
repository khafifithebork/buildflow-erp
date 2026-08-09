package com.buildflow.erp.domain.dashboard.dto.response;

import java.math.BigDecimal;

public record DashboardKpisResponse(
        String month,

        // Balance KPIs — as of now, not period-scoped.
        BigDecimal dettesFournisseursTtc,
        BigDecimal dettesSousTraitantsTtc,
        BigDecimal paieAPayerNet,
        BigDecimal attachementsEnCoursTtc,
        BigDecimal valeurStocksGlobaleHt,
        /** Split of the line above: stock still in the central dépôt. */
        BigDecimal valeurStocksDepotHt,
        /** Split of the line above: stock allocated to chantiers. */
        BigDecimal valeurStocksEnTravauxHt,

        // Flow KPIs — scoped to `month` when provided, all-time otherwise.
        BigDecimal decaissementsCaisseTtc,
        BigDecimal encaissementsGlobauxTtc,
        BigDecimal decaissementsGlobauxTtc,

        // Margin formulas.
        BigDecimal margeNetteComptableHt,
        BigDecimal margeEnCoursPrevisionnelleHt
) {}
