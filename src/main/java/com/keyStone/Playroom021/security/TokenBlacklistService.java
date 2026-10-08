package com.keyStone.Playroom021.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWTs are stateless by design, so "logout" normally just means the client
 * discards its token. To also support server-side invalidation (e.g. a
 * stolen or shared token), we keep a lightweight in-memory blacklist keyed
 * by token string, storing its expiry so entries can be swept.
 *
 * For a production, multi-instance deployment this should be backed by a
 * shared store (e.g. Redis) instead of an in-process map.
 */
@Service
public class TokenBlacklistService {

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token, long expiryEpochMs) {
        blacklist.put(token, expiryEpochMs);
    }

    public boolean isBlacklisted(String token) {
        Long expiry = blacklist.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }
}
