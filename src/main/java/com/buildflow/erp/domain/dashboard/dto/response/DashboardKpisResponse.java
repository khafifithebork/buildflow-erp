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
        /**
         * What each of the three debts above has already had settled against
         * it, cumulative and every payment mode included. The debt figure is
         * the remainder, so debt + settled is the total ever committed.
         */
        BigDecimal dettesFournisseursPayeTtc,
        BigDecimal dettesSousTraitantsPayeTtc,
        BigDecimal paieRegleeNet,
        BigDecimal attachementsEnCoursTtc,
        BigDecimal valeurStocksGlobaleHt,
        /** Split of the line above: material still available, wherever it is held. */
        BigDecimal valeurStocksDepotHt,
        /** Split of the line above: material already posé — incorporated into the works. */
        BigDecimal valeurStocksEnTravauxHt,
        /**
         * Le même stock, découpé autrement : par emplacement plutôt que par
         * disponibilité. Au dépôt = pas encore affecté à un chantier ; sur
         * chantiers = déjà sorti vers un chantier, posé ou non.
         *
         * <p>Purement informatif. Aucune des deux valeurs n'entre dans une
         * formule — seul valeurStocksGlobaleHt le fait, via la marge nette.
         */
        BigDecimal valeurStocksAuDepotHt,
        BigDecimal valeurStocksSurChantiersHt,

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
