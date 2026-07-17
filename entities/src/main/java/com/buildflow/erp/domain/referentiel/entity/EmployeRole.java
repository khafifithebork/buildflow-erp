package com.buildflow.erp.domain.referentiel.entity;

/**
 * HR/Functional roles. Distinct from the system auth Role enum.
 * Determines payroll brackets and site assignments, not ERP login access.
 */
public enum EmployeRole {
    ADMIN, RH, FINANCE, PM, ACHAT, CONDUCTEUR_TRAVAUX, CHEF_EQUIPE, OUVRIER
}