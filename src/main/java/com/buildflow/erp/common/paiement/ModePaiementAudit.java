package com.buildflow.erp.common.paiement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Records every payment-mode assignment and change.
 *
 * <p>Called both when a document is first paid (previous mode null) and when
 * the mode is corrected afterwards, so the trail shows the full sequence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModePaiementAudit {

    private final ModePaiementHistoriqueRepository repository;

    /**
     * Appends one line to the trail. A no-op when nothing actually changed, so
     * re-saving a document with the same mode does not pad the history.
     */
    @Transactional
    public void record(TypeDocumentPaiement type, UUID documentId, String documentRef,
                       ModePaiement ancien, ModePaiement nouveau) {

        if (ancien == nouveau) {
            return;
        }

        ModePaiementHistorique entry = new ModePaiementHistorique();
        entry.setTypeDocument(type);
        entry.setDocumentId(documentId);
        entry.setDocumentRef(documentRef);
        entry.setAncienMode(ancien);
        entry.setNouveauMode(nouveau);
        entry.setModifiePar(currentUserEmail());

        repository.save(entry);

        log.info("Mode de paiement {} {} : {} -> {} (par {})",
                type, documentRef, ancien, nouveau, entry.getModifiePar());
    }

    @Transactional(readOnly = true)
    public List<ModePaiementHistorique> historique(TypeDocumentPaiement type, UUID documentId) {
        return repository.findByTypeDocumentAndDocumentIdOrderByCreatedAtDesc(type, documentId);
    }

    /** Null rather than a placeholder when there is no authenticated user. */
    private static String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        return "anonymousUser".equals(name) ? null : name;
    }
}
