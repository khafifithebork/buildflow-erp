package com.buildflow.erp.common.export;

import com.buildflow.erp.common.export.ExcelWriter.Column;
import com.buildflow.erp.domain.achats.service.AchatService;
import com.buildflow.erp.domain.dashboard.dto.response.DashboardKpisResponse;
import com.buildflow.erp.domain.dashboard.service.DashboardService;
import com.buildflow.erp.domain.referentiel.service.ArticleService;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
import com.buildflow.erp.domain.referentiel.service.EmployeService;
import com.buildflow.erp.domain.referentiel.service.FournisseurService;
import com.buildflow.erp.domain.salaires.service.SalaireService;
import com.buildflow.erp.domain.soustraitance.service.SousTraitanceService;
import com.buildflow.erp.domain.tresorerie.service.TresorerieService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the .xlsx exports.
 *
 * <p>Every section knows how to add itself to a workbook, so exporting one
 * section and exporting the whole dashboard differ only in how many sections
 * are asked to contribute.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final DashboardService dashboardService;
    private final AchatService achatService;
    private final ChantierService chantierService;
    private final FournisseurService fournisseurService;
    private final ArticleService articleService;
    private final EmployeService employeService;
    private final TresorerieService tresorerieService;
    private final SalaireService salaireService;
    private final SousTraitanceService sousTraitanceService;

    /** One section of the dashboard that can be exported on its own. */
    public enum Section {
        INDICATEURS, ACHATS, CHANTIERS, FOURNISSEURS, ARTICLES,
        EMPLOYES, CAISSES, SALAIRES, SOUS_TRAITANCE
    }

    @Transactional(readOnly = true)
    public byte[] export(Section section, String month) {
        try (ExcelWriter writer = new ExcelWriter()) {
            addSection(writer, section, month);
            return writer.toBytes();
        }
    }

    /** Every section in one workbook, one sheet each. */
    @Transactional(readOnly = true)
    public byte[] exportTout(String month) {
        try (ExcelWriter writer = new ExcelWriter()) {
            for (Section section : Section.values()) {
                addSection(writer, section, month);
            }
            return writer.toBytes();
        }
    }

    private void addSection(ExcelWriter w, Section section, String month) {
        switch (section) {
            case INDICATEURS -> indicateurs(w, month);
            case ACHATS -> achats(w);
            case CHANTIERS -> chantiers(w);
            case FOURNISSEURS -> fournisseurs(w);
            case ARTICLES -> articles(w);
            case EMPLOYES -> employes(w);
            case CAISSES -> caisses(w);
            case SALAIRES -> salaires(w);
            case SOUS_TRAITANCE -> sousTraitance(w);
        }
    }

    private void indicateurs(ExcelWriter w, String month) {
        DashboardKpisResponse k = dashboardService.getKpis(month);
        List<String[]> rows = new ArrayList<>(List.of(
                new String[]{"Période", month == null || month.isBlank() ? "Tout l'historique" : month},
                new String[]{"Dettes fournisseurs (TTC)", k.dettesFournisseursTtc().toPlainString()},
                new String[]{"Dettes fournisseurs (HT)", k.dettesFournisseursHt().toPlainString()},
                new String[]{"Dettes sous-traitants (TTC)", k.dettesSousTraitantsTtc().toPlainString()},
                new String[]{"Dettes sous-traitants (HT)", k.dettesSousTraitantsHt().toPlainString()},
                new String[]{"Paie à payer (net)", k.paieAPayerNet().toPlainString()},
                new String[]{"Attachements en cours (TTC)", k.attachementsEnCoursTtc().toPlainString()},
                new String[]{"Valeur stocks globale (HT)", k.valeurStocksGlobaleHt().toPlainString()},
                new String[]{"— dont dépôts (HT)", k.valeurStocksDepotHt().toPlainString()},
                new String[]{"— dont en travaux (HT)", k.valeurStocksEnTravauxHt().toPlainString()},
                new String[]{"Décaissements caisse (TTC)", k.decaissementsCaisseTtc().toPlainString()},
                new String[]{"Encaissements globaux (TTC)", k.encaissementsGlobauxTtc().toPlainString()},
                new String[]{"Décaissements globaux (TTC)", k.decaissementsGlobauxTtc().toPlainString()},
                new String[]{"Décaissements globaux (HT)", k.decaissementsGlobauxHt().toPlainString()},
                new String[]{"Décaissements effet chantier (HT)", k.decaissementsEffetChantierHt().toPlainString()},
                new String[]{"Marge nette comptable (HT)", k.margeNetteComptableHt().toPlainString()},
                new String[]{"Résultat hors fiscalité (HT)", k.resultatHorsFiscaliteHt().toPlainString()},
                new String[]{"Marge en cours prévisionnelle (HT)", k.margeEnCoursPrevisionnelleHt().toPlainString()}));
        w.kpiSheet("Indicateurs", "Indicateurs du tableau de bord", List.of(), rows);
    }

    private void achats(ExcelWriter w) {
        w.sheet("Achats", "Commandes d'achat", achatService.findAll(), List.of(
                new Column<>("Réf", a -> a.ref()),
                new Column<>("Fournisseur", a -> a.fournisseurNom()),
                new Column<>("Chantier", a -> a.chantierNom()),
                new Column<>("Date commande", a -> a.dateCommande()),
                new Column<>("Livraison prévue", a -> a.dateLivraisonPrevue()),
                new Column<>("Statut", a -> a.status()),
                new Column<>("HT", a -> a.ht()),
                new Column<>("TVA", a -> a.tva()),
                new Column<>("TTC", a -> a.ttc()),
                new Column<>("Mode paiement", a -> a.modePaiement()),
                new Column<>("Effet chantier", a -> a.impactAnalytiqueChantier()),
                new Column<>("Effet fiscal", a -> a.impactComptableFiscal()),
                new Column<>("Réf BL", a -> a.bonLivraisonRef()),
                new Column<>("Réf facture", a -> a.factureRef())));
    }

    private void chantiers(ExcelWriter w) {
        w.sheet("Chantiers", "Chantiers", chantierService.findAll(), List.of(
                new Column<>("Code", c -> c.code()),
                new Column<>("Nom", c -> c.nom()),
                new Column<>("Client", c -> c.client()),
                new Column<>("Ville", c -> c.ville()),
                new Column<>("Statut", c -> c.statut()),
                new Column<>("Début", c -> c.dateDebut()),
                new Column<>("Fin prévue", c -> c.dateFin()),
                new Column<>("Budget HT", c -> c.budgetHt()),
                new Column<>("Dépenses HT", c -> c.depensesHt()),
                new Column<>("Avancement %", c -> c.avancement()),
                new Column<>("Chef de projet", c -> c.chefProjetNom())));
    }

    private void fournisseurs(ExcelWriter w) {
        w.sheet("Fournisseurs", "Fournisseurs", fournisseurService.findAll(), List.of(
                new Column<>("Code", f -> f.code()),
                new Column<>("Raison sociale", f -> f.raisonSociale()),
                new Column<>("ICE", f -> f.ice()),
                new Column<>("Ville", f -> f.ville()),
                new Column<>("Téléphone", f -> f.telephone()),
                new Column<>("Email", f -> f.email()),
                new Column<>("Statut", f -> f.statut()),
                new Column<>("Total achats année", f -> f.totalAchatsAnnee()),
                new Column<>("Solde impayé", f -> f.soldeImpaye())));
    }

    private void articles(ExcelWriter w) {
        w.sheet("Articles", "Catalogue articles",
                articleService.findAll(PageRequest.of(0, 5_000)).content(), List.of(
                        new Column<>("Code", a -> a.code()),
                        new Column<>("Désignation", a -> a.designation()),
                        new Column<>("Catégorie", a -> a.categorieLibelle()),
                        new Column<>("Unité", a -> a.unite()),
                        new Column<>("Prix achat réf.", a -> a.prixAchatRef()),
                        new Column<>("TVA %", a -> a.tvaRate()),
                        new Column<>("Actif", a -> a.actif())));
    }

    private void employes(ExcelWriter w) {
        w.sheet("Employés", "Annuaire des employés", employeService.findAll(), List.of(
                new Column<>("Matricule", e -> e.matricule()),
                new Column<>("Nom", e -> e.nom()),
                new Column<>("Prénom", e -> e.prenom()),
                new Column<>("Poste", e -> e.poste()),
                new Column<>("Département", e -> e.departement()),
                new Column<>("Rôle", e -> e.role()),
                new Column<>("Date embauche", e -> e.dateEmbauche()),
                new Column<>("Chantier actuel", e -> e.chantierActuelNom()),
                new Column<>("Statut", e -> e.statut()),
                new Column<>("Salaire brut", e -> e.salaireBrut()),
                new Column<>("Type contrat", e -> e.typeContrat())));
    }

    private void caisses(ExcelWriter w) {
        w.sheet("Caisses", "Caisses et soldes", tresorerieService.findAll(), List.of(
                new Column<>("Code", c -> c.code()),
                new Column<>("Libellé", c -> c.libelle()),
                new Column<>("Chantier", c -> c.chantierNom()),
                new Column<>("Solde", c -> c.solde()),
                new Column<>("Seuil minimum", c -> c.seuilMinimum()),
                new Column<>("En alerte", c -> c.enAlerte())));
    }

    private void salaires(ExcelWriter w) {
        w.sheet("Salaires", "Fiches de paie", salaireService.findAll(), List.of(
                new Column<>("Référence", f -> f.reference()),
                new Column<>("Matricule", f -> f.employeMatricule()),
                new Column<>("Employé", f -> f.employeNomComplet()),
                new Column<>("Chantier", f -> f.chantierNom()),
                new Column<>("Période", f -> f.periode()),
                new Column<>("Jours travaillés", f -> f.joursTravailles()),
                new Column<>("Salaire base", f -> f.salaireBase()),
                new Column<>("Net à payer", f -> f.netAPayer()),
                new Column<>("Statut", f -> f.statut()),
                new Column<>("Mode paiement", f -> f.modePaiement())));
    }

    private void sousTraitance(ExcelWriter w) {
        w.sheet("Sous-traitance", "Contrats de sous-traitance",
                sousTraitanceService.findAllContrats(), List.of(
                        new Column<>("Référence", c -> c.reference()),
                        new Column<>("Sous-traitant", c -> c.sousTraitantRaisonSociale()),
                        new Column<>("Chantier", c -> c.chantierNom()),
                        new Column<>("Objet", c -> c.objet()),
                        new Column<>("Montant HT", c -> c.montantHt()),
                        new Column<>("Montant TTC", c -> c.montantTtc()),
                        new Column<>("Montant payé", c -> c.montantPaye()),
                        new Column<>("Début", c -> c.dateDebut()),
                        new Column<>("Fin", c -> c.dateFin()),
                        new Column<>("Statut", c -> c.statut())));
    }
}
