package com.buildflow.erp.domain.referentiel.repository;

import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FournisseurRepository extends JpaRepository<Fournisseur, UUID> {
    Optional<Fournisseur> findByCode(String code);
    boolean existsByCode(String code);
}