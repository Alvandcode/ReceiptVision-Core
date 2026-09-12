package com.receiptvision.core.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByOwner(AppUser owner);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.owner = :owner and r.revoked = false")
    int revokeAllForOwner(AppUser owner);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now or r.revoked = true and r.createdAt < :revokedBefore")
    int deleteExpired(Instant now, Instant revokedBefore);
}
