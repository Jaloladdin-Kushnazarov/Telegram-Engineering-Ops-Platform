package com.engops.platform.identity.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 218a — TelegramLoginVerifier unit testlari.
 *
 * <p>Hash computation uchun {@link TelegramLoginVerifier#computeExpectedHash}
 * package-private helper ishlatiladi — ishlab chiqaruvchi va tekshiruvchi
 * tomonida bir xil mantiq, real Telegram payload imitatsiyasi.</p>
 */
class TelegramLoginVerifierTest {

    private static final String BOT_TOKEN = "1234567890:AAEhBP0av9faketokenfortestonly99";

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private static TelegramLoginPayload validPayload(long telegramId, long authDate) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", String.valueOf(telegramId));
        fields.put("first_name", "Davron");
        fields.put("auth_date", String.valueOf(authDate));
        String hash = TelegramLoginVerifier.computeExpectedHash(BOT_TOKEN, fields);
        return new TelegramLoginPayload(telegramId, "Davron", null, null, null,
                authDate, hash);
    }

    @Test
    void verify_validHash_returnsTrue() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        TelegramLoginPayload payload = validPayload(100_000_001L, now());

        assertThat(verifier.verify(payload)).isTrue();
    }

    @Test
    void verify_invalidHash_returnsFalse() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        TelegramLoginPayload payload = new TelegramLoginPayload(
                100_000_001L, "Davron", null, null, null, now(),
                "0000000000000000000000000000000000000000000000000000000000000000");

        assertThat(verifier.verify(payload)).isFalse();
    }

    @Test
    void verify_tamperedField_returnsFalse() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        long ad = now();
        TelegramLoginPayload original = validPayload(100_000_001L, ad);
        // Same hash but firstName changed → mismatch
        TelegramLoginPayload tampered = new TelegramLoginPayload(
                original.id(), "Tampered", null, null, null, ad, original.hash());

        assertThat(verifier.verify(tampered)).isFalse();
    }

    @Test
    void verify_expiredAuthDate_returnsFalse() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        long oldAuthDate = now() - TelegramLoginVerifier.MAX_AUTH_AGE_SECONDS - 100;
        TelegramLoginPayload payload = validPayload(100_000_001L, oldAuthDate);

        assertThat(verifier.verify(payload)).isFalse();
    }

    @Test
    void verify_freshAuthDate_returnsTrue() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        TelegramLoginPayload payload = validPayload(100_000_001L, now() - 30);

        assertThat(verifier.verify(payload)).isTrue();
    }

    @Test
    void verify_minimumFields_works() {
        // Faqat id + first_name + auth_date + hash
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        TelegramLoginPayload payload = validPayload(200_000_002L, now());

        assertThat(verifier.verify(payload)).isTrue();
    }

    @Test
    void verify_allFields_works() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        long ad = now();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", "300000003");
        fields.put("first_name", "Davron");
        fields.put("last_name", "Yusupov");
        fields.put("username", "dyusupov");
        fields.put("photo_url", "https://t.me/i/userpic/x.jpg");
        fields.put("auth_date", String.valueOf(ad));
        String hash = TelegramLoginVerifier.computeExpectedHash(BOT_TOKEN, fields);
        TelegramLoginPayload payload = new TelegramLoginPayload(
                300_000_003L, "Davron", "Yusupov", "dyusupov",
                "https://t.me/i/userpic/x.jpg", ad, hash);

        assertThat(verifier.verify(payload)).isTrue();
    }

    @Test
    void verify_disabled_throwsIllegalState() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest("");

        assertThatThrownBy(() -> verifier.verify(validPayload(1L, now())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bot-token");
    }

    @Test
    void isEnabled_returnsTrue_whenTokenSet() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        assertThat(verifier.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_returnsFalse_whenTokenEmpty() {
        assertThat(TelegramLoginVerifier.forTest("").isEnabled()).isFalse();
        assertThat(TelegramLoginVerifier.forTest("   ").isEnabled()).isFalse();
        assertThat(TelegramLoginVerifier.forTest(null).isEnabled()).isFalse();
    }

    @Test
    void dataCheckString_alphabeticallySorted() {
        // first_name, id, last_name → alfabetik tartibda first_name < id < last_name
        TelegramLoginPayload p = new TelegramLoginPayload(
                42L, "Alice", "Zilch", null, null, 1700000000L, "ignored");
        String s = TelegramLoginVerifier.buildDataCheckString(p);
        assertThat(s).isEqualTo(
                "auth_date=1700000000\n"
                + "first_name=Alice\n"
                + "id=42\n"
                + "last_name=Zilch");
    }

    @Test
    void verify_nullHash_returnsFalse() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        TelegramLoginPayload payload = new TelegramLoginPayload(
                1L, "X", null, null, null, now(), null);
        assertThat(verifier.verify(payload)).isFalse();
    }

    @Test
    void verify_malformedHexHash_returnsFalse() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        TelegramLoginPayload payload = new TelegramLoginPayload(
                1L, "X", null, null, null, now(), "not-hex-string");
        assertThat(verifier.verify(payload)).isFalse();
    }

    @Test
    void verify_nullId_returnsFalse() {
        TelegramLoginVerifier verifier = TelegramLoginVerifier.forTest(BOT_TOKEN);
        TelegramLoginPayload payload = new TelegramLoginPayload(
                null, "X", null, null, null, now(), "abcd");
        assertThat(verifier.verify(payload)).isFalse();
    }
}
