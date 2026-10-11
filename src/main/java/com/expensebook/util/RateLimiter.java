package com.expensebook.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe sliding window rate limiter to protect sensitive authentication endpoints
 * against brute-force attacks and abuse.
 */
public class RateLimiter {

    private final ConcurrentHashMap<String, List<Long>> requestLogs = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire a request slot for the given key.
     *
     * @param key         Unique identifier (e.g., client IP or email)
     * @param maxRequests Maximum requests allowed within window
     * @param windowMs    Window size in milliseconds
     * @return true if permitted, false if rate limited
     */
    public synchronized boolean tryAcquire(String key, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        long threshold = now - windowMs;

        List<Long> timestamps = requestLogs.computeIfAbsent(key, k -> new ArrayList<>());

        // Purge timestamps outside sliding window
        Iterator<Long> it = timestamps.iterator();
        while (it.hasNext()) {
            if (it.next() < threshold) {
                it.remove();
            } else {
                break;
            }
        }

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.add(now);
        return true;
    }

    /**
     * Resets rate limiter tracking for a specific key upon successful login or reset.
     */
    public void reset(String key) {
        requestLogs.remove(key);
    }
}
