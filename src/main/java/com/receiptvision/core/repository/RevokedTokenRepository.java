package com.receiptvision.core.repository;

import java.time.Instant;
import com.receiptvision.core.domain.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    @Modifying
    @Query("delete from RevokedToken r where r.expiresAt < :now")
    int deleteExpired(java.time.Instant now);

    default int deleteExpiredNow() {
        return deleteExpired(Instant.now());
    }
}
