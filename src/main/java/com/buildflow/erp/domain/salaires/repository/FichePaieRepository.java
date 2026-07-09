package com.buildflow.erp.domain.salaires.repository;

import com.buildflow.erp.domain.salaires.entity.FichePaie;
import com.buildflow.erp.domain.salaires.entity.FichePaieStatut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FichePaieRepository extends JpaRepository<FichePaie, UUID> {
    Optional<FichePaie> findByReference(String reference);
    boolean existsByReference(String reference);
    boolean existsByEmployeIdAndPeriode(UUID employeId, String periode);
    List<FichePaie> findByPeriode(String periode);
    List<FichePaie> findByStatut(FichePaieStatut statut);
    List<FichePaie> findByPeriodeAndStatut(String periode, FichePaieStatut statut);
}
