package com.receiptvision.core.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.receiptvision.core.domain.RevokedToken;
import com.receiptvision.core.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistent JWT denylist for logout: in-memory L1 + H2/JDBC L2.
 * Survives restarts (rows deleted once expired by hourly purge).
 */
@Service
public class TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklist.class);

    private final Map<String, Long> cache = new ConcurrentHashMap<>();
    private final RevokedTokenRepository repository;

    public TokenBlacklist(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void revoke(String jti, long expiryEpochMillis) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (expiryEpochMillis <= now) {
            return;
        }
        cache.put(jti, expiryEpochMillis);
        try {
            repository.save(new RevokedToken(jti, Instant.ofEpochMilli(expiryEpochMillis)));
        } catch (Exception e) {
            // Duplicate PK (already revoked) or transient DB issue: cache still protects.
            log.debug("RevokedToken save skipped for {}: {}", jti, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Long exp = cache.get(jti);
        long now = System.currentTimeMillis();
        if (exp != null) {
            if (exp < now) {
                cache.remove(jti);
            } else {
                return true;
            }
        }
        // L1 miss: check L2 (covers restarts).
        boolean inDb = repository.existsById(jti);
        if (inDb) {
            // Refresh L1 with DB expiry when available.
            repository.findById(jti).ifPresent(r -> cache.put(jti, r.getExpiresAt().toEpochMilli()));
            return true;
        }
        return false;
    }

    public int size() {
        return cache.size();
    }

    /** Hourly purge of expired rows + cache entries. */
    @Scheduled(fixedDelayString = "${app.purge-interval-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue() < now);
        try {
            int deleted = repository.deleteExpired(Instant.ofEpochMilli(now));
            if (deleted > 0) {
                log.info("Token denylist purge: removed {} expired entries", deleted);
            }
        } catch (Exception e) {
            log.debug("Token denylist purge skipped: {}", e.getMessage());
        }
    }
}
