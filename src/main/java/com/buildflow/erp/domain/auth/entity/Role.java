package com.buildflow.erp.domain.auth.entity;

public enum Role {
    ADMIN(100),
    DIRECTEUR(90),
    RH(80),
    PM(70),
    CHEF_CHANTIER(10),
    MAGASINIER(10),
    FINANCE(10),
    ACHAT(10),
    VIEWER(0);

    private final int level;

    Role(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean requiresApproval() {
        return this.level >= 70;
    }

    public boolean canApprove(Role requested) {
        return this.requiresApproval() && this.level >= requested.level;
    }
}