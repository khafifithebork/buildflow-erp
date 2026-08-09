package com.buildflow.erp.common.paiement;

import com.buildflow.erp.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Payment mode of an already-settled document, for all three document types.
 *
 * <pre>
 *   PATCH /api/v1/mode-paiement/ACHAT/{id}
 *   GET   /api/v1/mode-paiement/FICHE_PAIE/{id}/historique
 * </pre>
 *
 * Setting the mode for the first time happens on each module's own payment
 * endpoint; this is for correcting it afterwards.
 */
@RestController
@RequestMapping("/api/v1/mode-paiement")
@RequiredArgsConstructor
public class ModePaiementController {

    private final ModePaiementService modePaiementService;

    public record ChangerModePaiementRequest(@NotNull ModePaiement modePaiement) {}

    @PatchMapping("/{typeDocument}/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<ModePaiementResponse>> changer(
            @PathVariable TypeDocumentPaiement typeDocument,
            @PathVariable UUID documentId,
            @Valid @RequestBody ChangerModePaiementRequest request) {

        ModePaiementResponse response =
                modePaiementService.changer(typeDocument, documentId, request.modePaiement());

        return ResponseEntity.ok(response.avertissement() == null
                ? ApiResponse.success(response)
                : ApiResponse.success(response, response.avertissement()));
    }

    @GetMapping("/{typeDocument}/{documentId}/historique")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<List<ModePaiementHistoriqueResponse>>> historique(
            @PathVariable TypeDocumentPaiement typeDocument,
            @PathVariable UUID documentId) {

        List<ModePaiementHistoriqueResponse> trail =
                modePaiementService.historique(typeDocument, documentId).stream()
                        .map(ModePaiementHistoriqueResponse::from)
                        .toList();

        return ResponseEntity.ok(ApiResponse.success(trail));
    }
}
