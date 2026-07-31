package com.buildflow.erp.domain.comptabilite.service;

import com.buildflow.erp.domain.achats.entity.Achat;
import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.comptabilite.dto.response.EcritureComptableResponse;
import com.buildflow.erp.domain.comptabilite.dto.response.EcritureLigneResponse;
import com.buildflow.erp.domain.salaires.entity.FichePaie;
import com.buildflow.erp.domain.salaires.repository.FichePaieRepository;
import com.buildflow.erp.domain.soustraitance.entity.PaiementSousTraitant;
import com.buildflow.erp.domain.soustraitance.repository.PaiementSousTraitantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComptabiliteServiceImpl implements ComptabiliteService {

    private final AchatRepository achatRepository;
    private final FichePaieRepository fichePaieRepository;
    private final PaiementSousTraitantRepository paiementSousTraitantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EcritureComptableResponse> listEcritures() {
        List<EcritureComptableResponse> ecritures = new ArrayList<>();

        for (Achat a : achatRepository.findAll()) {
            ecritures.add(fromAchat(a));
        }
        for (FichePaie f : fichePaieRepository.findAll()) {
            ecritures.add(fromFichePaie(f));
        }
        for (PaiementSousTraitant p : paiementSousTraitantRepository.findAll()) {
            ecritures.add(fromPaiement(p));
        }

        ecritures.sort(Comparator.comparing(EcritureComptableResponse::date).reversed());
        return ecritures;
    }

    private EcritureComptableResponse fromAchat(Achat a) {
        List<EcritureLigneResponse> lignes = List.of(
                new EcritureLigneResponse(a.getId() + "-l1", "612100", "Achats matières premières", a.getHt(), BigDecimal.ZERO),
                new EcritureLigneResponse(a.getId() + "-l2", "345200", "TVA récupérable sur achats", a.getTva(), BigDecimal.ZERO),
                new EcritureLigneResponse(a.getId() + "-l3", "441100", "Fournisseurs", BigDecimal.ZERO, a.getTtc())
        );
        return new EcritureComptableResponse(
                a.getId().toString(),
                a.getDateCommande(),
                "ACH",
                a.getRef(),
                "Achat " + a.getFournisseur().getRaisonSociale(),
                lignes,
                a.getTtc(),
                "Système"
        );
    }

    private EcritureComptableResponse fromFichePaie(FichePaie f) {
        BigDecimal brut = f.getSalaireBase()
                .add(f.getMontantHeuresSupp())
                .add(f.getPrimeTransport())
                .add(f.getPrimePanier())
                .add(f.getAutresPrimes());

        List<EcritureLigneResponse> lignes = List.of(
                new EcritureLigneResponse(f.getId() + "-l1", "641100", "Rémunérations du personnel", brut, BigDecimal.ZERO),
                new EcritureLigneResponse(f.getId() + "-l2", "443000", "CNSS à payer", BigDecimal.ZERO, f.getDeductionsCnss()),
                new EcritureLigneResponse(f.getId() + "-l3", "444000", "IR à payer", BigDecimal.ZERO, f.getDeductionsIr()),
                new EcritureLigneResponse(f.getId() + "-l4", "444200", "Salaires nets à payer", BigDecimal.ZERO, f.getNetAPayer())
        );

        return new EcritureComptableResponse(
                f.getId().toString(),
                LocalDate.parse(f.getPeriode() + "-01"), // periode is "2026-07"
                "SAL",
                f.getReference(),
                "Charge salariale " + f.getPeriode() + " — " + f.getEmploye().getNom() + " " + f.getEmploye().getPrenom(),
                lignes,
                brut,
                "Système"
        );
    }

    private EcritureComptableResponse fromPaiement(PaiementSousTraitant p) {
        // Payments start EN_ATTENTE with no datePaiement yet — fall back to
        // createdAt so every écriture always has a real date to sort/display.
        LocalDate date = p.getDatePaiement() != null
                ? p.getDatePaiement()
                : p.getCreatedAt().toLocalDate();

        List<EcritureLigneResponse> lignes = List.of(
                new EcritureLigneResponse(p.getId() + "-l1", "613200", "Sous-traitance", p.getMontant(), BigDecimal.ZERO),
                new EcritureLigneResponse(p.getId() + "-l2", "441700", "Sous-traitants", BigDecimal.ZERO, p.getMontant())
        );

        return new EcritureComptableResponse(
                p.getId().toString(),
                date,
                "STR",
                p.getReference(),
                "Paiement sous-traitance — " + p.getMotif(),
                lignes,
                p.getMontant(),
                "Système"
        );
    }
}