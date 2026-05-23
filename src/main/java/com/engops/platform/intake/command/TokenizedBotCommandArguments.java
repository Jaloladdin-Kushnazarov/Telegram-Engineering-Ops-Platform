package com.engops.platform.intake.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 201 — quoted-string tokenizer for bot commands that need multi-word
 * argument values (masalan {@code /onboard acme "Acme Corp" 123 "Demo Admin" BUG_MINIMAL}).
 *
 * <p>Phase 200 dispatcher har bir text message'ni oddiy whitespace bo'yicha
 * ajratadi; bu yetarli emas tenant nomi yoki display name kabi ko'p so'zli
 * qiymatlar uchun. Bu utility class shu bo'shliqni to'ldiradi va FAQAT shu
 * kerakli command'lar tomonidan (masalan {@link OnboardCommand}) chaqiriladi.
 * Phase 200 dispatcher o'z xulqida o'zgarish qilmaydi.</p>
 *
 * <p><strong>Tokenizatsiya qoidalari:</strong></p>
 * <ul>
 *   <li>Tokenlar bir yoki bir nechta whitespace belgilari bilan ajratiladi
 *       (probel, tab, yangi qator).</li>
 *   <li>Qo'sh tirnoq ichidagi belgilar (jumladan whitespace) yagona token
 *       sifatida saqlanadi.</li>
 *   <li>Bitta tirnoq oddiy literal belgi (alohida ishlovga ega emas).</li>
 *   <li>Qo'sh tirnoq ichida {@code \} keyingi belgini "escape" qiladi:
 *       {@code \"} → {@code "}, {@code \\} → {@code \}, va boshqa har qanday
 *       belgi {@code \X} → {@code X} (defensive, literal qaytaradi).</li>
 *   <li>Tugallanmagan qo'sh tirnoq ({@code "} ochildi, lekin yopilmadi) →
 *       {@link IllegalArgumentException}.</li>
 *   <li>Birinchi token (command nomi) {@link #parse(String)} natijasidan
 *       tashlab yuboriladi.</li>
 * </ul>
 *
 * <p>Class utility — public konstruktor yo'q.</p>
 */
final class TokenizedBotCommandArguments {

    private TokenizedBotCommandArguments() {
        // utility — yaratilmasin.
    }

    /**
     * Tokenize raw bot command text and drop the leading command name token.
     *
     * @param rawText to'liq xabar matni (masalan {@code "/onboard acme \"Acme Corp\""})
     * @return command-dan keyingi argumentlar ro'yxati (immutable; bo'sh ham bo'lishi mumkin)
     * @throws IllegalArgumentException tugallanmagan qo'sh tirnoq bo'lsa
     */
    static List<String> parse(String rawText) {
        if (rawText == null) {
            return List.of();
        }
        List<String> all = tokenize(rawText);
        if (all.isEmpty()) {
            return List.of();
        }
        // Birinchi token — command nomi; uni tashlab yuboramiz.
        return List.copyOf(all.subList(1, all.size()));
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean tokenStarted = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '\\' && i + 1 < text.length()) {
                    // Escape: keyingi belgi literal sifatida qo'shiladi.
                    current.append(text.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    inQuotes = false;
                    i++;
                    continue;
                }
                current.append(c);
                i++;
                continue;
            }
            // Quote'lardan tashqarida.
            if (Character.isWhitespace(c)) {
                if (tokenStarted) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inQuotes = true;
                tokenStarted = true;
                i++;
                continue;
            }
            current.append(c);
            tokenStarted = true;
            i++;
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Tugallanmagan qo'sh tirnoq");
        }
        if (tokenStarted) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
