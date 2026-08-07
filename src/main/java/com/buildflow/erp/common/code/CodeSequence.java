package com.buildflow.erp.common.code;

/**
 * The catalogue of server-generated entity codes.
 *
 * <p>Each constant fixes a prefix and what the numbering resets on. Formats
 * follow the conventions the data already used, so codes created before and
 * after this feature read the same.
 *
 * <pre>
 *   FRN-001            master data, never resets
 *   CH-2026-001        documents, resets each calendar year
 *   FDP-2026-07-001    payslips, resets each payroll period
 *   BPU-001            resets per chantier (the chantier is NOT in the code)
 * </pre>
 */
public enum CodeSequence {

    // ── Master data: one running counter, no reset ──────────────────────
    FOURNISSEUR("FRN", Reset.NEVER),
    SOUS_TRAITANT("ST", Reset.NEVER),
    EMPLOYE("EMP", Reset.NEVER),
    ARTICLE("ART", Reset.NEVER),
    CATEGORIE_ARTICLE("CAT", Reset.NEVER),

    // ── Documents: counter restarts every calendar year ─────────────────
    CHANTIER("CH", Reset.YEARLY),
    ACHAT("ACH", Reset.YEARLY),
    CONTRAT_SOUS_TRAITANT("CST", Reset.YEARLY),
    PAIEMENT_SOUS_TRAITANT("PAI", Reset.YEARLY),
    ATTACHEMENT("ATT", Reset.YEARLY),

    // ── Caller supplies the discriminator ───────────────────────────────
    /** Resets per payroll period; the period appears in the code (FDP-2026-07-001). */
    FICHE_PAIE("FDP", Reset.PER_DISCRIMINATOR_IN_CODE);

    // Note: BpuLigne.ref is intentionally absent. Those refs come from the
    // client's tender document rather than from us, so they stay hand-entered.

    /** How often the counter restarts, and whether the discriminator is printed. */
    public enum Reset {
        NEVER,
        YEARLY,
        PER_DISCRIMINATOR_IN_CODE,
        PER_DISCRIMINATOR_HIDDEN
    }

    private final String prefix;
    private final Reset reset;

    CodeSequence(String prefix, Reset reset) {
        this.prefix = prefix;
        this.reset = reset;
    }

    public String prefix() {
        return prefix;
    }

    public Reset reset() {
        return reset;
    }

    public boolean requiresDiscriminator() {
        return reset == Reset.PER_DISCRIMINATOR_IN_CODE || reset == Reset.PER_DISCRIMINATOR_HIDDEN;
    }

    public boolean printsDiscriminator() {
        return reset == Reset.PER_DISCRIMINATOR_IN_CODE;
    }
}
