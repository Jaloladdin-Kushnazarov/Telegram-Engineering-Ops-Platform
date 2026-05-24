package com.engops.platform.identity.auth;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 218a — Telegram Login Widget orqali autentifikatsiya pipeline.
 *
 * <p>Quyidagi qadamlar atomik bajariladi:</p>
 * <ol>
 *   <li>{@link TelegramLoginVerifier#isEnabled()} tekshiruvi
 *       (bot token sozlanganmi?)</li>
 *   <li>{@link TelegramLoginPayload#id()} null emas tasdig'i</li>
 *   <li>{@link TelegramLoginVerifier#verify(TelegramLoginPayload)} —
 *       HMAC + auth_date 24h tekshiruvi</li>
 *   <li>find-or-create {@link AppUser} (telegram_user_id bo'yicha)</li>
 *   <li>{@link TelegramLoginTokenIssuer#issueToken} bilan JWT chiqarish</li>
 * </ol>
 *
 * <p>Har xato {@link TelegramLoginException} sifatida tashlanadi —
 * controller'da 401 Unauthorized'ga aylantiriladi. Internal sabab
 * (verifier disabled, hash mismatch, expired auth_date) UI'ga
 * tushmaydi — operator faqat "Invalid login" ko'radi (security
 * by obscurity emas, lekin attack surface'ni kichraytirish).</p>
 *
 * <p><strong>Conditional dependency:</strong>
 * {@link TelegramLoginTokenIssuer} {@code @ConditionalOnProperty} bilan
 * faollashadi (jwt.hmac-secret). Agar property yo'q bo'lsa, issuer
 * bean ham yo'q va {@link ObjectProvider#getIfAvailable()} null qaytaradi —
 * service "not configured" javob beradi.</p>
 */
@Service
@Transactional
public class TelegramLoginService {

    private static final Logger log = LoggerFactory.getLogger(TelegramLoginService.class);

    private final TelegramLoginVerifier verifier;
    private final AppUserRepository appUserRepository;
    private final ObjectProvider<TelegramLoginTokenIssuer> tokenIssuerProvider;

    public TelegramLoginService(TelegramLoginVerifier verifier,
                                 AppUserRepository appUserRepository,
                                 ObjectProvider<TelegramLoginTokenIssuer> tokenIssuerProvider) {
        this.verifier = verifier;
        this.appUserRepository = appUserRepository;
        this.tokenIssuerProvider = tokenIssuerProvider;
    }

    /**
     * Telegram payload'ni autentifikatsiya qiladi va JWT chiqaradi.
     *
     * @throws TelegramLoginException agar verification fail, server
     *         sozlanmagan yoki majburiy maydon null
     */
    public String authenticate(TelegramLoginPayload payload) {
        if (!verifier.isEnabled()) {
            log.warn("Telegram login attempt rejected: server not configured");
            throw new TelegramLoginException(
                    "Telegram login bu serverda sozlanmagan");
        }
        if (payload == null || payload.id() == null) {
            throw new TelegramLoginException("Telegram user id majburiy");
        }

        TelegramLoginTokenIssuer tokenIssuer = tokenIssuerProvider.getIfAvailable();
        if (tokenIssuer == null) {
            log.warn("Telegram login rejected: token issuer bean missing "
                    + "(jwt.hmac-secret yo'q)");
            throw new TelegramLoginException(
                    "Telegram login bu serverda sozlanmagan");
        }

        if (!verifier.verify(payload)) {
            log.warn("Telegram login: hash verification fail telegram_id={}",
                    payload.id());
            throw new TelegramLoginException("Imzo noto'g'ri yoki muddati o'tgan");
        }

        AppUser user = appUserRepository.findByTelegramUserId(payload.id())
                .orElseGet(() -> createAppUser(payload));

        log.info("Telegram login success: user={} telegram_id={}",
                user.getId(), payload.id());

        return tokenIssuer.issueToken(user.getId(), payload.id());
    }

    private AppUser createAppUser(TelegramLoginPayload payload) {
        String displayName = payload.firstName() != null
                ? payload.firstName()
                : "User";
        if (payload.lastName() != null && !payload.lastName().isBlank()) {
            displayName = displayName + " " + payload.lastName();
        }
        AppUser user = new AppUser(payload.id(), displayName);
        if (payload.username() != null && !payload.username().isBlank()) {
            user.setUsername(payload.username());
        }
        return appUserRepository.save(user);
    }
}
