package com.buildflow.erp.domain.tresorerie.repository;

import com.buildflow.erp.domain.tresorerie.entity.CaisseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaisseTransactionRepository extends JpaRepository<CaisseTransaction, UUID> {
    List<CaisseTransaction> findByCaisseIdOrderByCreatedAtDesc(UUID caisseId);
}
