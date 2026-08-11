package com.buildflow.erp.domain.dashboard.service;

import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.attachement.repository.AttachementRepository;
import com.buildflow.erp.domain.dashboard.dto.response.DashboardKpisResponse;
import com.buildflow.erp.domain.salaires.repository.FichePaieRepository;
import com.buildflow.erp.domain.soustraitance.repository.ContratSousTraitantRepository;
import com.buildflow.erp.domain.soustraitance.repository.PaiementSousTraitantRepository;
import com.buildflow.erp.domain.stock.repository.StockArticleRepository;
import com.buildflow.erp.domain.tresorerie.repository.CaisseTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AchatRepository achatRepository;
    private final ContratSousTraitantRepository contratSousTraitantRepository;
    private final PaiementSousTraitantRepository paiementSousTraitantRepository;
    private final FichePaieRepository fichePaieRepository;
    private final CaisseTransactionRepository caisseTransactionRepository;
    private final StockArticleRepository stockArticleRepository;
    private final AttachementRepository attachementRepository;

    private static final LocalDate ALL_TIME_START = LocalDate.of(1970, 1, 1);
    private static final LocalDate ALL_TIME_END = LocalDate.of(9999, 12, 31);

    @Override
    @Transactional(readOnly = true)
    public DashboardKpisResponse getKpis(String month) {
        YearMonth ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : null;

        LocalDate dateStart = ym != null ? ym.atDay(1) : ALL_TIME_START;
        LocalDate dateEnd = ym != null ? ym.atEndOfMonth() : ALL_TIME_END;
        LocalDateTime dtStart = LocalDateTime.of(dateStart, LocalTime.MIN);
        LocalDateTime dtEnd = LocalDateTime.of(dateEnd, LocalTime.MAX);

        // ── Balance KPIs (as of now) ─────────────────────────────────
        BigDecimal dettesFournisseursTtc = round(achatRepository.sumTtcNonPayees());
        BigDecimal dettesSousTraitantsTtc = round(contratSousTraitantRepository.sumResteAPayer());
        // HT readings of the same debts, used by the margin formulas below.
        BigDecimal dettesFournisseursHt = round(achatRepository.sumHtNonPayees());
        BigDecimal dettesSousTraitantsHt = round(contratSousTraitantRepository.sumResteAPayerHt());
        BigDecimal paieAPayerNet = round(fichePaieRepository.sumNetAPayerNonPayees());
        BigDecimal attachementsEnCoursTtc = round(attachementRepository.sumTtcSoumis());
        BigDecimal attachementsEnCoursHt = round(attachementRepository.sumHtSoumis());
        // Stock valuation comes back as a double (prices are DOUBLE PRECISION);
        // pin it to two decimals here, where it becomes a money figure.
        BigDecimal valeurStocksGlobaleHt = round(
                BigDecimal.valueOf(stockArticleRepository.sumValeurStockHt()));
        // Same valuation split by location, so the dashboard's Dépôts /
        // En Travaux figures are computed rather than hardcoded to zero.
        // Dépôts = still available, En Travaux = already posé. The split is by
        // availability, not by location, and the two always sum to the total.
        BigDecimal valeurStocksDepotHt = round(
                BigDecimal.valueOf(stockArticleRepository.sumValeurStockDispoHt()));
        BigDecimal valeurStocksEnTravauxHt = round(
                BigDecimal.valueOf(stockArticleRepository.sumValeurStockTravauxHt()));

        // ── Flow KPIs (scoped to `month`, or all-time when absent) ───
        BigDecimal decaissementsCaisseTtc = round(caisseTransactionRepository.sumDebitsBetween(dtStart, dtEnd));
        BigDecimal encaissementsGlobauxTtc = round(attachementRepository.sumTtcEncaisseBetween(dtStart, dtEnd));
        BigDecimal encaissementsGlobauxHt = round(attachementRepository.sumHtEncaisseBetween(dtStart, dtEnd));

        BigDecimal achatsPayeesTtc = round(achatRepository.sumTtcPayeesBetween(dateStart, dateEnd));
        BigDecimal stPayeesTtc = round(paiementSousTraitantRepository.sumPayeesBetween(dateStart, dateEnd));
        BigDecimal salairesPayeesNet = round(ym != null
                ? fichePaieRepository.sumNetAPayerPayeesByPeriode(month)
                : fichePaieRepository.sumNetAPayerPayeesAllTime());

        // "Bank + cash" decaissements approximated as caisse debits plus
        // settled achats/sous-traitance/salaires — there is no explicit
        // payment-source (virement vs. caisse) tracking yet (doc gap 2.8),
        // so paid amounts recorded outside a caisse are assumed bank transfers.
        BigDecimal decaissementsGlobauxTtc = decaissementsCaisseTtc
                .add(achatsPayeesTtc)
                .add(stPayeesTtc)
                .add(salairesPayeesNet);

        // Same outflows with the tax stripped out. Only achats carry a
        // separable TVA — caisse debits, sous-traitance payments and net
        // salaries are recorded as single amounts with no tax breakdown — so
        // the achats component swaps to HT and the rest passes through
        // unchanged. That is the whole of the "hors fiscalité" adjustment, and
        // the difference between the two figures is the recoverable TVA on
        // settled purchases.
        BigDecimal achatsPayeesHt = round(achatRepository.sumHtPayeesBetween(dateStart, dateEnd));
        BigDecimal decaissementsGlobauxHt = decaissementsCaisseTtc
                .add(achatsPayeesHt)
                .add(stPayeesTtc)
                .add(salairesPayeesNet);

        // Outflows retained by the hors-fiscalité reading. The two operational
        // indicators decide membership: an operation counts when it genuinely
        // served the site (effet chantier) and carries no official invoice to
        // declare (effet fiscal). Anything fiscal drops out entirely.
        //
        // Only achats and caisse operations carry these flags, so they are the
        // only outflows that can be classified. Sous-traitance payments and
        // salaries have no such marking and are therefore not counted here —
        // "on ne compte que l'effet chantier" read literally: unmarked is not
        // marked.
        BigDecimal decaissementsEffetChantierHt =
                round(achatRepository.sumHtPayeesEffetChantierBetween(dateStart, dateEnd))
                        .add(round(caisseTransactionRepository
                                .sumDebitsEffetChantierBetween(dtStart, dtEnd)));

        // ── Margin formulas ───────────────────────────────────────────
        // Every term HT: the outflows use the tax-free reading rather than TTC.
        BigDecimal margeNetteComptableHt = round(
                encaissementsGlobauxHt.subtract(decaissementsGlobauxHt).add(valeurStocksGlobaleHt));

        // Operational flows only: stock is a balance-sheet position, not a
        // flow, so it is deliberately absent here even though the marge nette
        // above includes it.
        BigDecimal resultatHorsFiscaliteHt = round(
                encaissementsGlobauxHt.subtract(decaissementsEffetChantierHt));

        // Also fully HT. Net salaries carry no TVA, so paieAPayerNet is already
        // a tax-free figure and needs no HT counterpart.
        BigDecimal margeEnCoursPrevisionnelleHt = round(
                attachementsEnCoursHt.subtract(
                        dettesFournisseursHt.add(dettesSousTraitantsHt).add(paieAPayerNet)));

        return new DashboardKpisResponse(
                month,
                dettesFournisseursTtc,
                dettesSousTraitantsTtc,
                dettesFournisseursHt,
                dettesSousTraitantsHt,
                paieAPayerNet,
                attachementsEnCoursTtc,
                valeurStocksGlobaleHt,
                valeurStocksDepotHt,
                valeurStocksEnTravauxHt,
                decaissementsCaisseTtc,
                encaissementsGlobauxTtc,
                decaissementsGlobauxTtc,
                decaissementsGlobauxHt,
                decaissementsEffetChantierHt,
                margeNetteComptableHt,
                resultatHorsFiscaliteHt,
                margeEnCoursPrevisionnelleHt);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
