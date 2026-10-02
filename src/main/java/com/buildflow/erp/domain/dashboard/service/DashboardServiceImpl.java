package com.buildflow.erp.domain.dashboard.service;

import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.achats.repository.PaiementAchatRepository;
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
    private final PaiementAchatRepository paiementAchatRepository;
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
        // Le pendant réglé de chacune des trois dettes ci-dessus. Cumulatif et
        // tous modes de paiement confondus : la carte montre ce qui est soldé
        // face à ce qui reste, et les deux doivent redonner le total engagé.
        BigDecimal dettesFournisseursPayeTtc = round(achatRepository.sumTtcPayees());
        BigDecimal dettesSousTraitantsPayeTtc = round(contratSousTraitantRepository.sumMontantPayeTtc());
        BigDecimal paieRegleeNet = round(fichePaieRepository.sumNetAPayerPayees());
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
        // Le même stock découpé par emplacement. Les deux requêtes existaient
        // sans jamais sortir du backend. Purement informatif : elles n'entrent
        // dans aucune formule, seul le global le fait via la marge nette.
        BigDecimal valeurStocksAuDepotHt = round(
                BigDecimal.valueOf(stockArticleRepository.sumValeurStockAuDepotHt()));
        BigDecimal valeurStocksSurChantiersHt = round(
                BigDecimal.valueOf(stockArticleRepository.sumValeurStockSurChantiersHt()));
        // Le stock de l'effet chantier, celui que retient la marge nette.
        //
        // Il vaut le stock global tant que le schema ne permet pas mieux :
        // mouvements_stock ne porte aucune cle vers l'achat qui l'a cree, donc
        // rien ne permet d'ecarter le stock n'existant que par l'effet fiscal.
        // La valeur est volontairement calculee a part plutot qu'en reutilisant
        // valeurStocksGlobaleHt : le jour ou le lien existe, seule cette ligne
        // bouge et les deux marges divergent d'elles-memes.
        BigDecimal valeurStocksEffetChantierHt = valeurStocksGlobaleHt;

        // ── Flow KPIs (scoped to `month`, or all-time when absent) ───
        BigDecimal decaissementsCaisseTtc = round(caisseTransactionRepository.sumDebitsBetween(dtStart, dtEnd));
        BigDecimal encaissementsGlobauxTtc = round(attachementRepository.sumTtcEncaisseBetween(dtStart, dtEnd));
        BigDecimal encaissementsGlobauxHt = round(attachementRepository.sumHtEncaisseBetween(dtStart, dtEnd));

        // Les reglements portent desormais leur propre date, lue du registre
        // paiements_achat. Avant, faute de mieux, la periode se decidait sur
        // dateCommande — la date a laquelle la commande avait ete passee, pas
        // celle a laquelle l'argent etait sorti.
        BigDecimal achatsPayeesTtc = round(paiementAchatRepository.sumPayeesBetween(dateStart, dateEnd));
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

        // Les mêmes sorties, lues hors taxes. Deux des quatre sources portent
        // une TVA séparable et basculent en HT ; les deux autres n'en portent
        // aucune et passent telles quelles :
        //
        //   achats          a.ht, la TVA vit à côté sur la ligne
        //   sous-traitance  au prorata du ratio HT/TTC de chaque contrat
        //   caisse          dépense d'espèces, pas de facture derrière
        //   paie            le net à payer ; un salaire ne porte pas de TVA
        //
        // La sous-traitance entrait ici à son montant TTC. Le commentaire qui
        // le justifiait affirmait qu'elle n'avait « pas de ventilation fiscale »
        // — c'est faux : ContratSousTraitant porte montantHt, tva et montantTtc
        // depuis toujours. Le total hors taxes était donc gonflé de la TVA
        // versée aux sous-traitants, ce qui minorait d'autant la marge nette et
        // le résultat hors fiscalité.
        BigDecimal achatsPayeesHt = round(paiementAchatRepository.sumPayeesHtBetween(dateStart, dateEnd));
        BigDecimal stPayeesHt = round(paiementSousTraitantRepository.sumPayeesHtBetween(dateStart, dateEnd));
        BigDecimal decaissementsGlobauxHt = decaissementsCaisseTtc
                .add(achatsPayeesHt)
                .add(stPayeesHt)
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
                round(paiementAchatRepository.sumPayeesHtEffetChantierBetween(dateStart, dateEnd))
                        .add(round(caisseTransactionRepository
                                .sumDebitsEffetChantierBetween(dtStart, dtEnd)));

        // ── Margin formulas ───────────────────────────────────────────
        // Calcul 1 — la situation reelle d'exploitation.
        //
        //   encaissements reels HT
        // - decaissements reels HT (achats HT + sous-traitance HT + paie + caisse)
        // + stock de l'effet chantier
        //
        // La paie et la caisse entrent a leur montant : elles ne portent pas de
        // TVA, il n'y a rien a reconvertir.
        BigDecimal margeNetteComptableHt = round(
                encaissementsGlobauxHt.subtract(decaissementsGlobauxHt)
                        .add(valeurStocksEffetChantierHt));

        // Calcul 2 — la lecture globale.
        //
        //   encaissements reels HT - decaissement global + stock global
        //
        // Memes decaissements que le calcul 1 : tout ce qui est reellement
        // sorti, effet chantier comme effet fiscal, sans filtre de drapeau.
        // Ce qui le separe du calcul 1, c'est le perimetre du stock — global
        // ici, limite a l'effet chantier la-bas.
        //
        // Les deux indicateurs rendent donc le meme montant tant que le stock
        // de l'effet chantier ne peut pas etre isole. C'est assume : la
        // structure est en place, et ils divergeront d'eux-memes le jour ou
        // mouvements_stock portera une cle vers son achat.
        //
        // decaissementsEffetChantierHt reste calcule : l'export Excel le porte
        // encore comme colonne a part entiere.
        BigDecimal resultatHorsFiscaliteHt = round(
                encaissementsGlobauxHt.subtract(decaissementsGlobauxHt)
                        .add(valeurStocksGlobaleHt));

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
                dettesFournisseursPayeTtc,
                dettesSousTraitantsPayeTtc,
                paieRegleeNet,
                attachementsEnCoursTtc,
                valeurStocksGlobaleHt,
                valeurStocksDepotHt,
                valeurStocksEnTravauxHt,
                valeurStocksAuDepotHt,
                valeurStocksSurChantiersHt,
                valeurStocksEffetChantierHt,
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
