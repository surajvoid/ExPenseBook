package com.expensebook.util;

import com.expensebook.dao.UserDAO;
import com.expensebook.model.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for generating and verifying cryptographic stateless session tokens.
 * Survived across server restarts, container redeployments, and device sleep cycles.
 */
public class SessionTokenUtil {

    private static final String HMAC_ALGO = "HmacSHA256";
    // Session token stays valid for 90 days
    private static final long TOKEN_VALIDITY_MS = 90L * 24 * 60 * 60 * 1000L;
    private static final String DEFAULT_SERVER_SECRET = "ExpenseBook-Stateless-Session-Secret-Production-2026-RockSolid";
    private static final String SERVER_SECRET;

    static {
        String secret = System.getenv("SESSION_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getenv("JWT_SECRET");
        }
        if (secret == null || secret.trim().isEmpty()) {
            File secretFile = new File(".expensebook_secret");
            if (!secretFile.exists()) {
                secretFile = new File(System.getProperty("user.home"), ".expensebook_secret");
            }
            if (secretFile.exists() && secretFile.canRead()) {
                try {
                    secret = Files.readString(secretFile.toPath()).trim();
                } catch (Exception ignored) {}
            }
        }
        if (secret == null || secret.trim().isEmpty()) {
            // Use deterministic strong secret so ephemeral containers on Render retain session validity across restarts
            secret = DEFAULT_SERVER_SECRET;
            try {
                File target = new File(".expensebook_secret");
                if (!target.exists()) {
                    Files.writeString(target.toPath(), secret);
                }
            } catch (Exception ignored) {}
        }
        SERVER_SECRET = secret;
    }

    /**
     * Generates a signed stateless session token for the user.
     * Token format: <base64url_payload>.<base64url_hmac_signature>
     */
    public static String generateToken(User user) {
        if (user == null || user.getId() <= 0) {
            return null;
        }
        long expiry = System.currentTimeMillis() + TOKEN_VALIDITY_MS;
        String nonce = Long.toHexString(new SecureRandom().nextLong());
        String payload = user.getId() + ":" + expiry + ":" + nonce;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedPayload, user.getPasswordHash());
        return encodedPayload + "." + signature;
    }

    /**
     * Verifies a session token, checks expiry, and returns the User if valid.
     * Uses constant-time comparison against user's password hash.
     */
    public static User verifyToken(String token, UserDAO userDAO) {
        if (token == null || !token.contains(".") || userDAO == null) {
            return null;
        }
        try {
            int dotIdx = token.indexOf('.');
            String encodedPayload = token.substring(0, dotIdx);
            String signature = token.substring(dotIdx + 1);

            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            if (parts.length < 2) {
                return null;
            }

            int userId = Integer.parseInt(parts[0]);
            long expiry = Long.parseLong(parts[1]);

            if (System.currentTimeMillis() > expiry) {
                // Token has expired
                return null;
            }

            User user = userDAO.findById(userId);
            if (user == null || !user.isActive()) {
                return null;
            }

            String expectedSig = sign(encodedPayload, user.getPasswordHash());
            if (MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expectedSig.getBytes(StandardCharsets.UTF_8))) {
                return user;
            }
        } catch (Exception e) {
            // Malformed token or invalid signature
        }
        return null;
    }

    private static String sign(String data, String userSecret) {
        try {
            String keyString = SERVER_SECRET + ":" + (userSecret != null ? userSecret : "");
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(keyString.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error signing session token", e);
        }
    }
}
