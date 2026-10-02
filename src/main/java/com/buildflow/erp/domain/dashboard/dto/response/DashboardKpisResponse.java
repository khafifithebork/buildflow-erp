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
        /**
         * Le stock retenu par la marge nette comptable : celui qui releve de
         * l'effet chantier, hors montants n'existant que par l'effet fiscal.
         *
         * <p>Egal au stock global pour l'instant, et c'est une limite connue,
         * pas un choix : {@code mouvements_stock} ne porte aucune cle vers
         * l'achat qui l'a cree, seulement une reference en texte libre. Tant
         * que ce lien n'existe pas, aucun stock ne peut etre rattache a un
         * achat porteur du drapeau fiscal.
         *
         * <p>Le champ existe quand meme separement : le jour ou le lien est
         * pose, seule la requete change, pas les formules.
         */
        BigDecimal valeurStocksEffetChantierHt,

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
