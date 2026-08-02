package com.buildflow.erp.domain.attachement.repository;

import com.buildflow.erp.domain.attachement.entity.Attachement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AttachementRepository extends JpaRepository<Attachement, UUID> {

    List<Attachement> findByChantierIdOrderByDateAttachementDesc(UUID chantierId);

    boolean existsByChantierIdAndReference(UUID chantierId, String reference);

    // Attachements en cours: submitted but not yet encaissé, as of now.
    @Query("""
            SELECT COALESCE(SUM(a.montantTtc), 0) FROM Attachement a
            WHERE a.statut = com.buildflow.erp.domain.attachement.entity.AttachementStatut.SOUMIS
            """)
    BigDecimal sumTtcSoumis();

    @Query("""
            SELECT COALESCE(SUM(a.montantHt), 0) FROM Attachement a
            WHERE a.statut = com.buildflow.erp.domain.attachement.entity.AttachementStatut.SOUMIS
            """)
    BigDecimal sumHtSoumis();

    @Query("""
            SELECT COALESCE(SUM(a.montantTtc), 0) FROM Attachement a
            WHERE a.statut = com.buildflow.erp.domain.attachement.entity.AttachementStatut.ENCAISSE
            AND a.dateEncaissement BETWEEN :start AND :end
            """)
    BigDecimal sumTtcEncaisseBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT COALESCE(SUM(a.montantHt), 0) FROM Attachement a
            WHERE a.statut = com.buildflow.erp.domain.attachement.entity.AttachementStatut.ENCAISSE
            AND a.dateEncaissement BETWEEN :start AND :end
            """)
    BigDecimal sumHtEncaisseBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
