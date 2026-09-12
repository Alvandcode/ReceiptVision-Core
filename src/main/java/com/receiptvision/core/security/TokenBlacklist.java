package com.receiptvision.core.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory JWT denylist for logout.
 * Stores jti -&gt; expiryEpochMillis. Entries expire lazily on read.
 * For single-instance H2 deployment this is sufficient; for multi-instance
 * use Redis/JDBC instead.
 */
@Service
public class TokenBlacklist {

    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    public void revoke(String jti, long expiryEpochMillis) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        revoked.put(jti, expiryEpochMillis);
        purgeExpired();
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Long exp = revoked.get(jti);
        if (exp == null) {
            return false;
        }
        if (exp < System.currentTimeMillis()) {
            revoked.remove(jti);
            return false;
        }
        return true;
    }

    public int size() {
        purgeExpired();
        return revoked.size();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        revoked.entrySet().removeIf(e -> e.getValue() < now);
    }
}
