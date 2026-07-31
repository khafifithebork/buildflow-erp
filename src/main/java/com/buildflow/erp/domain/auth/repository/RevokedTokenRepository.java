package com.buildflow.erp.domain.auth.repository;

import com.buildflow.erp.domain.auth.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    boolean existsByJti(String jti);

    // Best-effort cleanup of entries whose tokens have already expired (and are
    // therefore rejected by the normal expiry check anyway).
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
