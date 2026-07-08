package com.buildflow.erp.domain.referentiel.repository;

import com.buildflow.erp.domain.referentiel.entity.Employe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EmployeRepository extends JpaRepository<Employe, UUID> {
    boolean existsByMatricule(String matricule);
}