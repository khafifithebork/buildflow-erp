package com.buildflow.erp.common.paiement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One immutable line of the payment-mode audit trail.
 *
 * <p>Written whenever a document's mode is set or changed, including the first
 * assignment at payment time (where {@code ancienMode} is null). Rows are never
 * updated or deleted — that is the point of an audit trail.
 *
 * <p>Deliberately not a {@code BaseEntity}: it has no {@code updated_at},
 * because nothing ever updates it.
 */
@Entity
@Table(name = "mode_paiement_historique")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ModePaiementHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false, length = 30)
    private TypeDocumentPaiement typeDocument;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** Human-readable ref of the document, so the trail stays legible if it is deleted. */
    @Column(name = "document_ref", length = 50)
    private String documentRef;

    /** Null on the first assignment — there was no previous mode. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ancien_mode", length = 20)
    private ModePaiement ancienMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "nouveau_mode", nullable = false, length = 20)
    private ModePaiement nouveauMode;

    /** Email of the user who made the change; null for system-driven changes. */
    @Column(name = "modifie_par", length = 255)
    private String modifiePar;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
