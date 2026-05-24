package com.engops.platform.identity.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Phase 218a — Telegram Login Widget REST endpoint.
 *
 * <p>SecurityConfig'da {@code /api/auth/telegram-login} permitAll —
 * JWT talab qilinmaydi. Foydalanuvchi Telegram orqali login qilgandan
 * keyin shu endpoint chaqiriladi va JWT olib qoladi.</p>
 *
 * <p><strong>Response shape:</strong></p>
 * <ul>
 *   <li>200 OK + {@code {"token": "<jwt>"}} — muvaffaqiyat</li>
 *   <li>401 Unauthorized + {@code {"error": "<message>"}} — verification fail</li>
 * </ul>
 *
 * <p>Frontend (Phase 218b widget) JSON'ni o'qib JWT'ni localStorage'ga
 * saqlaydi va dashboard'ga redirect qiladi.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class TelegramLoginController {

    private final TelegramLoginService loginService;

    public TelegramLoginController(TelegramLoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/telegram-login")
    public ResponseEntity<Map<String, String>> telegramLogin(
            @RequestBody TelegramLoginPayload payload) {
        try {
            String token = loginService.authenticate(payload);
            return ResponseEntity.ok(Map.of("token", token));
        } catch (TelegramLoginException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
