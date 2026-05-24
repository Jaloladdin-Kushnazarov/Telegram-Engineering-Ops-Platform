package com.engops.platform.identity.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Phase 218a — Telegram Login Widget hash tekshiruvchi.
 *
 * <p>Protokol: <a href="https://core.telegram.org/widgets/login#checking-authorization">
 * Telegram Login docs</a>.</p>
 *
 * <p>Algoritm:</p>
 * <ol>
 *   <li>{@code data_check_string} — barcha maydonlar (hash'dan tashqari)
 *       alfabetik tartibda, {@code key=value} ko'rinishida, {@code \n}
 *       bilan birlashtiriladi.</li>
 *   <li>{@code secret_key = SHA-256(bot_token)}</li>
 *   <li>{@code hmac = HMAC-SHA256(secret_key, data_check_string)}</li>
 *   <li>{@code hmac.hex()} payload'dagi {@code hash} bilan constant-time
 *       solishtiruv (timing attack ehtiyot choralari).</li>
 *   <li>{@code auth_date} 24 soatdan eski bo'lsa — rad etiladi.</li>
 * </ol>
 *
 * <p><strong>Conditional activation:</strong>
 * {@code app.security.telegram.bot-token} property bo'sh bo'lsa,
 * {@link #isEnabled()} false qaytaradi va {@link #verify(TelegramLoginPayload)}
 * IllegalStateException tashlaydi. Dev mode'da production bo'lmagani
 * uchun bu property mavjudligini ataylab default qilishadi
 * ({@code app.security.telegram.bot-token=} bo'sh).</p>
 */
@Component
public class TelegramLoginVerifier {

    /** Telegram Login Widget hash 24 soatdan kech bo'lmasligi shart. */
    static final long MAX_AUTH_AGE_SECONDS = 86_400L;

    /** SHA-256(bot_token) — null agar bot-token sozlanmagan. */
    private final byte[] secretKey;

    public TelegramLoginVerifier(
            @Value("${app.security.telegram.bot-token:}") String botToken) {
        if (botToken == null || botToken.isBlank()) {
            this.secretKey = null;
        } else {
            this.secretKey = sha256(botToken.getBytes(StandardCharsets.UTF_8));
        }
    }

    public boolean isEnabled() {
        return secretKey != null;
    }

    /**
     * Telegram payload'ning hash'ini va auth_date'ini tekshiradi.
     *
     * @return true agar hash mos va auth_date 24 soat ichida
     * @throws IllegalStateException agar bot token sozlanmagan bo'lsa
     */
    public boolean verify(TelegramLoginPayload payload) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "Telegram login sozlanmagan (bot-token bo'sh)");
        }
        if (payload == null || payload.hash() == null || payload.hash().isBlank()) {
            return false;
        }
        if (payload.authDate() == null) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (now - payload.authDate() > MAX_AUTH_AGE_SECONDS) {
            return false;
        }
        if (payload.id() == null) {
            return false;
        }

        String dataCheckString = buildDataCheckString(payload);
        byte[] expectedHash = hmacSha256(secretKey,
                dataCheckString.getBytes(StandardCharsets.UTF_8));
        byte[] receivedHash;
        try {
            receivedHash = hexDecode(payload.hash());
        } catch (IllegalArgumentException ex) {
            return false; // malformed hex
        }
        return MessageDigest.isEqual(expectedHash, receivedHash);
    }

    /**
     * data_check_string'ni alfabetik tartibda quradi. Telegram protokoli
     * snake_case maydon nomlarini ishlatadi (id, first_name, last_name,
     * username, photo_url, auth_date) — qattiq invariant.
     *
     * <p>{@code hash} maydoni HAR DOIM chiqarib tashlanadi (algoritm o'zi).</p>
     */
    static String buildDataCheckString(TelegramLoginPayload p) {
        TreeMap<String, String> fields = new TreeMap<>();
        if (p.id() != null) {
            fields.put("id", String.valueOf(p.id()));
        }
        if (p.firstName() != null) {
            fields.put("first_name", p.firstName());
        }
        if (p.lastName() != null) {
            fields.put("last_name", p.lastName());
        }
        if (p.username() != null) {
            fields.put("username", p.username());
        }
        if (p.photoUrl() != null) {
            fields.put("photo_url", p.photoUrl());
        }
        if (p.authDate() != null) {
            fields.put("auth_date", String.valueOf(p.authDate()));
        }
        return fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    // ========== Crypto helpers ==========

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algoritmi mavjud emas", ex);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 algoritmi mavjud emas", ex);
        }
    }

    static byte[] hexDecode(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex string null");
        }
        int len = hex.length();
        if ((len & 1) == 1) {
            throw new IllegalArgumentException("hex uzunligi juft bo'lishi shart");
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("hex string'da noto'g'ri belgi");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    // ========== Test helpers (package-private) ==========

    /**
     * Test'lar uchun: verifier'ni manual'ga bot token bilan yaratish.
     * Production code'da @Value orqali ishlatiladi.
     */
    static TelegramLoginVerifier forTest(String botToken) {
        return new TelegramLoginVerifier(botToken);
    }

    /**
     * Test'lar uchun: HMAC hash compute qiluvchi yordamchi. Ishlab
     * chiqaruvchi va tekshiruvchi tomonida bir xil mantiqdan foydalanish
     * uchun hex string sifatida qaytariladi.
     */
    static String computeExpectedHash(String botToken, Map<String, String> fields) {
        byte[] key = sha256(botToken.getBytes(StandardCharsets.UTF_8));
        String dataCheckString = new TreeMap<>(fields).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        byte[] hmac = hmacSha256(key, dataCheckString.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hmac.length * 2);
        for (byte b : hmac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
