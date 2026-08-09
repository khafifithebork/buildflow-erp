package com.buildflow.erp.common.paiement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModePaiementHistoriqueRepository extends JpaRepository<ModePaiementHistorique, UUID> {

    List<ModePaiementHistorique> findByTypeDocumentAndDocumentIdOrderByCreatedAtDesc(
            TypeDocumentPaiement typeDocument, UUID documentId);
}
