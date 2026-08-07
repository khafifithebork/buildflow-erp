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
        BigDecimal paieAPayerNet = round(fichePaieRepository.sumNetAPayerNonPayees());
        BigDecimal attachementsEnCoursTtc = round(attachementRepository.sumTtcSoumis());
        BigDecimal attachementsEnCoursHt = round(attachementRepository.sumHtSoumis());
        // Stock valuation comes back as a double (prices are DOUBLE PRECISION);
        // pin it to two decimals here, where it becomes a money figure.
        BigDecimal valeurStocksGlobaleHt = round(
                BigDecimal.valueOf(stockArticleRepository.sumValeurStockHt()));

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

        // ── Margin formulas ───────────────────────────────────────────
        BigDecimal margeNetteComptableHt = round(
                encaissementsGlobauxHt.subtract(decaissementsGlobauxTtc).add(valeurStocksGlobaleHt));

        BigDecimal margeEnCoursPrevisionnelleHt = round(
                attachementsEnCoursHt.subtract(
                        dettesFournisseursTtc.add(dettesSousTraitantsTtc).add(paieAPayerNet)));

        return new DashboardKpisResponse(
                month,
                dettesFournisseursTtc,
                dettesSousTraitantsTtc,
                paieAPayerNet,
                attachementsEnCoursTtc,
                valeurStocksGlobaleHt,
                decaissementsCaisseTtc,
                encaissementsGlobauxTtc,
                decaissementsGlobauxTtc,
                margeNetteComptableHt,
                margeEnCoursPrevisionnelleHt);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
