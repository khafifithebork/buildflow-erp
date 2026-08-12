package com.buildflow.erp.common.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Le taux de TVA appliqué par défaut, et le calcul qui va avec.
 *
 * <p>Il était recopié à l'identique dans trois services — achats, attachements,
 * sous-traitance — ce qui veut dire qu'un changement de taux devait être trouvé
 * trois fois. Un seul endroit désormais.
 *
 * <p>« Par défaut » se lit au sens strict : c'est le taux retenu quand la pièce
 * n'en porte pas de plus précis. Une ligne de commande, elle, porte le taux de
 * son article — voir {@code LigneAchat.tvaRate}.
 */
public final class Tva {

    /** 10 %. */
    public static final BigDecimal TAUX_PAR_DEFAUT = new BigDecimal("0.10");

    private Tva() {
    }

    /** La TVA due sur un montant hors taxes, au taux donné, arrondie au centime. */
    public static BigDecimal sur(BigDecimal montantHt, BigDecimal taux) {
        return montantHt.multiply(taux).setScale(2, RoundingMode.HALF_UP);
    }

    /** La TVA due au taux par défaut. */
    public static BigDecimal sur(BigDecimal montantHt) {
        return sur(montantHt, TAUX_PAR_DEFAUT);
    }
}
