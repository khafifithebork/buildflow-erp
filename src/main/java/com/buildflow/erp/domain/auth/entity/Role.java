package com.buildflow.erp.domain.auth.entity;

public enum Role {
    ADMIN(100, true),
    DIRECTEUR(90, true),
    RH(80, true),
    PM(70, true),
    CHEF_CHANTIER(10, false),
    MAGASINIER(10, false),
    FINANCE(10, true),
    ACHAT(10, false),
    VIEWER(0, false);

    private final int level;
    private final boolean requiresApproval;

    Role(int level, boolean requiresApproval) {
        this.level = level;
        this.requiresApproval = requiresApproval;
    }

    public int getLevel() {
        return level;
    }

    // Whether a self-registered account of this role must be approved before it can
    // log in. Kept independent of `level`: FINANCE is a low-level role (it must not
    // be able to approve anyone) yet still requires approval because it reaches
    // money-moving modules.
    public boolean requiresApproval() {
        return requiresApproval;
    }

    // Whether *this* role may approve/reject a pending signup of `requested`.
    // Only roles that themselves require approval can ever be approvers.
    // FINANCE signups are sensitive: only ADMIN, DIRECTEUR, or FINANCE may act on
    // them. FINANCE, in turn, may only approve other FINANCE accounts — it is not
    // part of the management ladder. Everyone else follows the ladder: an approver
    // may act on roles at or below its own level.
    public boolean canApprove(Role requested) {
        if (!this.requiresApproval) {
            return false;
        }
        if (requested == FINANCE) {
            return this == ADMIN || this == DIRECTEUR || this == FINANCE;
        }
        if (this == FINANCE) {
            return false;
        }
        return this.level >= requested.level;
    }
}
