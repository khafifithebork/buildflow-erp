package com.buildflow.erp.common.code;

/**
 * Allocates the next code for an entity, so codes are never typed by hand.
 *
 * <p>Allocation joins the caller's transaction: if creation rolls back, the
 * number is released rather than burned.
 */
public interface CodeGenerator {

    /**
     * Next code for a sequence that resets never or yearly.
     *
     * @throws IllegalArgumentException if the sequence needs a discriminator
     */
    String next(CodeSequence sequence);

    /**
     * Next code for a sequence scoped to something the caller knows — the
     * payroll period for {@code FICHE_PAIE}, the chantier id for
     * {@code BPU_LIGNE}.
     */
    String next(CodeSequence sequence, String discriminator);
}
