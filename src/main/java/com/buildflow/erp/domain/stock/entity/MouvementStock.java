package com.buildflow.erp.domain.stock.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.achats.entity.LigneAchat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "mouvements_stock")
@Getter
@Setter
@NoArgsConstructor
public class MouvementStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_article_id", nullable = false)
    private StockArticle stockArticle;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_mouvement", nullable = false, length = 20)
    private TypeMouvement typeMouvement;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantite;

    @Column(name = "document_ref", length = 100)
    private String documentRef;

    /**
     * La ligne de commande qui a produit cette entrée, quand il y en a une.
     *
     * <p>Elle porte les deux choses dont la valorisation par origine a besoin :
     * le prix d'entrée, que ce mouvement ne stocke pas, et les indicateurs
     * effet chantier / effet fiscal de la commande.
     *
     * <p>Nulle sur tout le reste — entrée saisie à la main, transfert,
     * ajustement. Une entrée sans ligne n'a pas d'origine connue, et
     * {@link com.buildflow.erp.domain.stock.repository.StockArticleRepository#valeursEffetChantierParLigne()}
     * la compte au dénominateur sans jamais l'attribuer : ne rien affirmer
     * plutôt que deviner.
     *
     * <p>{@code document_ref} reste en place, et reste la seule trace sur un
     * transfert ou une saisie. Les deux ne se contredisent pas : la référence
     * décrit, la clé rattache.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ligne_achat_id")
    private LigneAchat ligneAchat;
}