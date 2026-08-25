package com.buildflow.erp.domain.salaires.entity;

/**
 * Where a demande de paie stands.
 *
 * <p>Deliberately one step shorter than {@link FichePaieStatut}: a demande is
 * typed in already complete, so it enters the workflow submitted rather than
 * as a draft.
 */
public enum DemandePaieStatut {
    SOUMISE,
    VALIDEE,
    PAYEE
}
