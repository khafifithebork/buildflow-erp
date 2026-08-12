package com.buildflow.erp.domain.tresorerie.service;

import com.buildflow.erp.common.dto.UpdateIndicateursRequest;
import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.paiement.TypeDocumentPaiement;
import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.buildflow.erp.domain.bpu.repository.BpuLigneRepository;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateCaisseRequest;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateTransactionRequest;
import com.buildflow.erp.domain.tresorerie.dto.response.CaisseResponse;
import com.buildflow.erp.domain.tresorerie.dto.response.CaisseTransactionResponse;
import com.buildflow.erp.domain.tresorerie.entity.Caisse;
import com.buildflow.erp.domain.tresorerie.entity.CaisseTransaction;
import com.buildflow.erp.domain.tresorerie.entity.TypeTransaction;
import com.buildflow.erp.domain.tresorerie.mapper.CaisseMapper;
import com.buildflow.erp.domain.tresorerie.repository.CaisseRepository;
import com.buildflow.erp.domain.tresorerie.repository.CaisseTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TresorerieServiceImpl implements TresorerieService {

    private final CaisseRepository caisseRepository;
    private final CaisseTransactionRepository transactionRepository;
    private final ChantierRepository chantierRepository;
    private final BpuLigneRepository bpuLigneRepository;
    private final CaisseMapper caisseMapper;

    @Override
    @Transactional
    public CaisseResponse createCaisse(CreateCaisseRequest request) {
        Chantier chantier = chantierRepository.findById(request.chantierId())
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", request.chantierId()));

        Caisse caisse = new Caisse();
        caisse.setCode(deriveCaisseCode(chantier));
        caisse.setLibelle(request.libelle());
        caisse.setChantier(chantier);
        caisse.setSeuilMinimum(request.seuilMinimum());

        return caisseMapper.toResponse(caisseRepository.save(caisse));
    }

    @Override
    @Transactional(readOnly = true)
    public CaisseResponse findById(UUID id) {
        Caisse caisse = caisseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caisse", id));
        return caisseMapper.toResponse(caisse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaisseResponse> findAll() {
        return caisseRepository.findAll().stream()
                .map(caisseMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CaisseTransactionResponse enregistrerTransaction(UUID caisseId, CreateTransactionRequest request) {
        Caisse caisse = caisseRepository.findById(caisseId)
                .orElseThrow(() -> new ResourceNotFoundException("Caisse", caisseId));

        BpuLigne bpuLigne = null;
        if (request.bpuLigneId() != null) {
            bpuLigne = bpuLigneRepository.findById(request.bpuLigneId())
                    .orElseThrow(() -> new ResourceNotFoundException("BpuLigne", request.bpuLigneId()));
        }

        CaisseTransaction created = applyTransaction(caisse, request.typeTransaction(), request.montant(),
                request.motif(), request.referenceDocument(), bpuLigne,
                Boolean.TRUE.equals(request.impactAnalytiqueChantier()),
                Boolean.TRUE.equals(request.impactComptableFiscal()),
                false, AUCUN);

        return caisseMapper.toTransactionResponse(created);
    }

    @Override
    @Transactional
    public CaisseTransactionResponse annulerTransaction(UUID caisseId, UUID transactionId, String motif) {
        CaisseTransaction origine = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("CaisseTransaction", transactionId));

        if (!origine.getCaisse().getId().equals(caisseId)) {
            throw new ResourceNotFoundException("CaisseTransaction", transactionId);
        }
        if (origine.isAnnule()) {
            throw new BusinessRuleException(
                    "Cette opération a déjà été annulée.");
        }
        if (origine.isAjustement()) {
            throw new BusinessRuleException(
                    "Une écriture de correction ne s'annule pas : annulez l'opération d'origine.");
        }
        // Même règle, un cran plus haut : une écriture qui règle un document
        // n'est pas une opération autonome. La contre-passer seule remettrait
        // l'argent en caisse en laissant la commande payée — la dette
        // disparaîtrait ET le décaissement avec.
        if (origine.estDerivee()) {
            throw new BusinessRuleException(
                    "Cette écriture règle la commande " + origine.getReferenceDocument()
                            + " : annulez le paiement depuis la commande, pas depuis la caisse.");
        }

        // Reverse it: a credit undoes a debit and the other way round. Posted
        // as its own flagged movement rather than deleting the original, so the
        // ledger keeps both what happened and what corrected it — and the
        // décaissements netting already knows to discount a flagged credit.
        String libelle = motif != null && !motif.isBlank()
                ? "Annulation : " + motif
                : "Annulation : " + origine.getMotif();

        contrePasser(origine, libelle);
        CaisseTransaction saved = transactionRepository.save(origine);

        log.info("Opération de caisse annulée : {} {} sur {} ({})",
                origine.getTypeTransaction(), origine.getMontant(),
                origine.getCaisse().getCode(), libelle);

        return caisseMapper.toTransactionResponse(saved);
    }

    @Override
    @Transactional
    public CaisseTransactionResponse updateTransactionIndicateurs(
            UUID caisseId, UUID transactionId, UpdateIndicateursRequest request) {

        CaisseTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("CaisseTransaction", transactionId));

        if (!txn.getCaisse().getId().equals(caisseId)) {
            throw new ResourceNotFoundException("CaisseTransaction", transactionId);
        }

        // Null = "leave unchanged", so a single checkbox can be toggled alone.
        if (request.impactAnalytiqueChantier() != null) {
            txn.setImpactAnalytiqueChantier(request.impactAnalytiqueChantier());
        }
        if (request.impactComptableFiscal() != null) {
            txn.setImpactComptableFiscal(request.impactComptableFiscal());
        }

        return caisseMapper.toTransactionResponse(transactionRepository.save(txn));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaisseTransactionResponse> getTransactions(UUID caisseId) {
        if (!caisseRepository.existsById(caisseId)) {
            throw new ResourceNotFoundException("Caisse", caisseId);
        }
        return transactionRepository.findByCaisseIdOrderByCreatedAtDesc(caisseId).stream()
                .map(caisseMapper::toTransactionResponse)
                .toList();
    }

    @Override
    @Transactional
    public void debiterPourDocument(UUID chantierId, TypeDocumentPaiement typeDocument,
                                    UUID documentId, BigDecimal montant, String reference,
                                    boolean impactAnalytiqueChantier, boolean impactComptableFiscal) {

        Caisse caisse = getOrCreateCaisse(chantierId);

        applyTransaction(
                caisse,
                TypeTransaction.DEBIT,
                montant,
                libelleDe(typeDocument),
                reference,
                null,
                impactAnalytiqueChantier,
                impactComptableFiscal,
                false,
                new Document(typeDocument, documentId)
        );
    }

    private static String libelleDe(TypeDocumentPaiement type) {
        return switch (type) {
            case ACHAT -> "Paiement achat";
            case PAIEMENT_SOUS_TRAITANT -> "Paiement sous-traitant";
            case FICHE_PAIE -> "Paiement salaire";
        };
    }

    @Override
    @Transactional
    public void ajusterPourAchat(UUID chantierId, UUID achatId, BigDecimal delta, String achatRef,
                                 boolean impactAnalytiqueChantier, boolean impactComptableFiscal) {

        if (delta.signum() == 0) {
            return;
        }

        Caisse caisse = getOrCreateCaisse(chantierId);

        // A correction is recorded as its own movement rather than by editing
        // the original debit: the ledger stays append-only and the adjustment
        // is visible for what it is.
        boolean coutEnHausse = delta.signum() > 0;
        applyTransaction(
                caisse,
                coutEnHausse ? TypeTransaction.DEBIT : TypeTransaction.CREDIT,
                delta.abs(),
                coutEnHausse ? "Ajustement achat (hausse)" : "Ajustement achat (baisse)",
                achatRef,
                null,
                impactAnalytiqueChantier,
                impactComptableFiscal,
                true,
                new Document(TypeDocumentPaiement.ACHAT, achatId)
        );
    }

    @Override
    @Transactional
    public void debiterPourSalaire(UUID chantierId, BigDecimal montant, String reference) {

        Caisse caisse = getOrCreateCaisse(chantierId);

        applyTransaction(
                caisse,
                TypeTransaction.DEBIT,
                montant,
                "Paiement salaire",
                reference,
                null,
                // Payroll is not a purchase: neither indicator applies by default.
                // Finance can still tick them afterwards from the Caisse view.
                false,
                false,
                false,
                AUCUN
        );
    }

    @Override
    @Transactional
    public BigDecimal annulerEcrituresDuDocument(UUID documentId, String motif) {
        List<CaisseTransaction> ecritures = transactionRepository
                .findByDocumentIdAndAnnuleFalse(documentId);

        // Le règlement et ses éventuels ajustements forment un tout : ce qui est
        // sorti pour cette commande, c'est leur somme. Les contre-passer toutes
        // ramène la caisse là où elle serait si la commande n'avait jamais été
        // payée — y compris quand un changement de prix a suivi le paiement.
        BigDecimal net = BigDecimal.ZERO;
        for (CaisseTransaction ecriture : ecritures) {
            contrePasser(ecriture, "Annulation : " + motif);
            net = ecriture.getTypeTransaction() == TypeTransaction.DEBIT
                    ? net.add(ecriture.getMontant())
                    : net.subtract(ecriture.getMontant());
        }
        transactionRepository.saveAll(ecritures);

        log.info("{} écriture(s) de caisse annulée(s) pour le document {} : {} rendus",
                ecritures.size(), documentId, net);
        return net;
    }

    // ── Private ────────────────────────────────────────────────────

    /**
     * Passe l'écriture inverse et marque l'originale annulée. Le grand livre
     * garde les deux : ce qui a été enregistré, et ce qui l'a corrigé. La
     * contrepartie est marquée comme ajustement, ce que le calcul des
     * décaissements sait déjà déduire.
     */
    private void contrePasser(CaisseTransaction origine, String libelle) {
        TypeTransaction inverse = origine.getTypeTransaction() == TypeTransaction.DEBIT
                ? TypeTransaction.CREDIT
                : TypeTransaction.DEBIT;

        applyTransaction(
                origine.getCaisse(),
                inverse,
                origine.getMontant(),
                libelle,
                origine.getReferenceDocument(),
                origine.getBpuLigne(),
                origine.isImpactAnalytiqueChantier(),
                origine.isImpactComptableFiscal(),
                true,
                new Document(origine.getTypeDocument(), origine.getDocumentId())
        );

        origine.setAnnule(true);
    }

    /**
     * A caisse is named after the chantier it belongs to — {@code CAISSE-CH-2026-001}
     * says far more at a glance than a bare running number would.
     *
     * <p>Chantier creation auto-provisions the first one, so the base name is
     * normally free; a chantier given a second caisse gets {@code …-2}, {@code …-3}.
     */
    private String deriveCaisseCode(Chantier chantier) {
        String base = "CAISSE-" + chantier.getCode();
        if (!caisseRepository.existsByCode(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base + "-" + suffix;
            if (!caisseRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("Impossible de générer un code de caisse pour le chantier " + chantier.getCode());
    }

    private Caisse getOrCreateCaisse(UUID chantierId) {

        return caisseRepository.findByChantierId(chantierId)
                .stream()
                .findFirst()
                .orElseGet(() -> {

                    Chantier chantier = chantierRepository.findById(chantierId)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException("Chantier", chantierId));

                    Caisse caisse = new Caisse();

                    caisse.setCode("CAISSE-" + chantier.getCode());
                    caisse.setLibelle("Caisse " + chantier.getNom());
                    caisse.setChantier(chantier);
                    caisse.setSolde(BigDecimal.ZERO);
                    caisse.setSeuilMinimum(BigDecimal.ZERO);

                    return caisseRepository.save(caisse);
                });
    }

    /**
     * Le document qu'une écriture règle, ou {@link #AUCUN} pour une opération
     * autonome. Regroupé en un couple plutôt qu'en deux paramètres de plus :
     * le type sans l'identifiant, ou l'inverse, ne veut rien dire.
     */
    private record Document(com.buildflow.erp.common.paiement.TypeDocumentPaiement type, UUID id) {}

    private static final Document AUCUN = new Document(null, null);

    private CaisseTransaction applyTransaction(Caisse caisse, TypeTransaction type, BigDecimal montant,
                                               String motif, String referenceDocument, BpuLigne bpuLigne,
                                               boolean impactAnalytiqueChantier, boolean impactComptableFiscal,
                                               boolean ajustement, Document document) {
        if (type == TypeTransaction.DEBIT) {
            BigDecimal newSolde = caisse.getSolde().subtract(montant);
            if (newSolde.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException(
                        String.format("Insufficient funds in caisse '%s'. Solde: %s, Debit: %s",
                                caisse.getCode(), caisse.getSolde(), montant));
            }
            caisse.setSolde(newSolde);

            if (caisse.getSeuilMinimum().compareTo(BigDecimal.ZERO) > 0
                    && newSolde.compareTo(caisse.getSeuilMinimum()) <= 0) {
                log.warn("⚠️ ALERTE SEUIL: Caisse '{}' balance ({}) is at or below minimum threshold ({})",
                        caisse.getCode(), newSolde, caisse.getSeuilMinimum());
            }
        } else {
            caisse.setSolde(caisse.getSolde().add(montant));
        }

        caisseRepository.save(caisse);

        CaisseTransaction txn = new CaisseTransaction();
        txn.setCaisse(caisse);
        txn.setTypeTransaction(type);
        txn.setMontant(montant);
        txn.setMotif(motif);
        txn.setReferenceDocument(referenceDocument);
        txn.setTypeDocument(document.type());
        txn.setDocumentId(document.id());
        txn.setBpuLigne(bpuLigne);
        txn.setImpactAnalytiqueChantier(impactAnalytiqueChantier);
        txn.setImpactComptableFiscal(impactComptableFiscal);
        txn.setAjustement(ajustement);
        return transactionRepository.save(txn);
    }
}
