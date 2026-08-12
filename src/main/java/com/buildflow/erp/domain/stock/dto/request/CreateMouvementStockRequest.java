package com.buildflow.erp.domain.stock.dto.request;

import com.buildflow.erp.domain.stock.entity.TypeMouvement;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMouvementStockRequest(
        @NotNull UUID articleId,

        /**
         * Where the movement applies. Null means the central dépôt, so an
         * ENTREE with no chantier receives goods into the warehouse.
         */
        UUID chantierId,

        @NotNull TypeMouvement typeMouvement,

        /**
         * Signée pour un {@code AJUSTEMENT} seulement : un écart d'inventaire se
         * constate dans les deux sens, et le borner au positif obligeait à
         * saisir un manquant en {@code SORTIE}, où il se mélangeait aux
         * consommations réelles.
         *
         * <p>Une entrée, une sortie ou un transfert portent leur sens dans leur
         * type et gardent une quantité positive. La règle dépend donc du type,
         * ce qu'une contrainte de champ ne sait pas exprimer : c'est le service
         * qui la vérifie.
         */
        @NotNull BigDecimal quantite,
        String documentRef,

        /**
         * Destination, for {@code TRANSFERT} only. Null means the central
         * dépôt, so a transfer can move stock either way between the warehouse
         * and a site. Must differ from {@code chantierId}.
         */
        UUID chantierDestinationId
) {}
