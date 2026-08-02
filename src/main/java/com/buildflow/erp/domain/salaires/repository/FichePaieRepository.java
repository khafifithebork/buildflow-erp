package com.buildflow.erp.domain.salaires.repository;

import com.buildflow.erp.domain.salaires.entity.FichePaie;
import com.buildflow.erp.domain.salaires.entity.FichePaieStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    @Query("""
            SELECT COALESCE(SUM(f.netAPayer), 0) FROM FichePaie f
            WHERE f.bpuLigne.id = :bpuLigneId
            AND f.statut = com.buildflow.erp.domain.salaires.entity.FichePaieStatut.PAYEE
            """)
    BigDecimal sumMontantEngageByBpuLigneId(@Param("bpuLigneId") UUID bpuLigneId);

    // Paie à payer: fiches not yet paid, as of now (not period-scoped).
    @Query("""
            SELECT COALESCE(SUM(f.netAPayer), 0) FROM FichePaie f
            WHERE f.statut <> com.buildflow.erp.domain.salaires.entity.FichePaieStatut.PAYEE
            """)
    BigDecimal sumNetAPayerNonPayees();

    @Query("""
            SELECT COALESCE(SUM(f.netAPayer), 0) FROM FichePaie f
            WHERE f.statut = com.buildflow.erp.domain.salaires.entity.FichePaieStatut.PAYEE
            """)
    BigDecimal sumNetAPayerPayeesAllTime();

    @Query("""
            SELECT COALESCE(SUM(f.netAPayer), 0) FROM FichePaie f
            WHERE f.statut = com.buildflow.erp.domain.salaires.entity.FichePaieStatut.PAYEE
            AND f.periode = :periode
            """)
    BigDecimal sumNetAPayerPayeesByPeriode(@Param("periode") String periode);
}
