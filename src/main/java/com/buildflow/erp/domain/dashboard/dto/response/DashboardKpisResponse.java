package com.buildflow.erp.domain.dashboard.dto.response;

import java.math.BigDecimal;

public record DashboardKpisResponse(
        String month,

        // Balance KPIs — as of now, not period-scoped.
        BigDecimal dettesFournisseursTtc,
        BigDecimal dettesSousTraitantsTtc,
        /** Same debts read net of tax — what the margin formulas use. */
        BigDecimal dettesFournisseursHt,
        BigDecimal dettesSousTraitantsHt,
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
        /** Same outflows net of the recoverable TVA on settled purchases. */
        BigDecimal decaissementsGlobauxHt,
        /**
         * Outflows retained by the hors-fiscalité reading: operations flagged
         * effet chantier and not effet fiscal.
         */
        BigDecimal decaissementsEffetChantierHt,

        // Margin formulas.
        BigDecimal margeNetteComptableHt,
        /** The margin read entirely on HT — no TVA on either side. */
        BigDecimal resultatHorsFiscaliteHt,
        BigDecimal margeEnCoursPrevisionnelleHt
) {}
