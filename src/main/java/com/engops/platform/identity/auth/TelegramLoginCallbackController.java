package com.engops.platform.identity.auth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Phase 218d — Telegram Login Widget <strong>redirect mode</strong> callback.
 *
 * <p>Phase 218b/c callback mode (data-onauth + iframe postMessage) ngrok
 * orqali ishonchsiz edi — cross-origin postMessage silent fail qilardi va
 * JWT hech qachon localStorage'ga tushmasdi. Phase 218d widget'ni redirect
 * mode'ga ({@code data-auth-url}) o'tkazadi: Telegram browser'ni
 * to'g'ridan-to'g'ri shu endpoint'ga query params bilan yo'naltiradi —
 * iframe yoki callback yo'q, faqat oddiy HTTP redirect.</p>
 *
 * <p>{@code @Controller} (NOT {@code @RestController}) — HTML view nomi
 * qaytaradi. {@code /web/login/**} SecurityConfig'da {@code /web/**}
 * permitAll qamrovida (Phase 207'dan beri) — JWT talab qilinmaydi, chunki
 * foydalanuvchi hali login qilmagan. Himoya {@link TelegramLoginService}
 * ichidagi HMAC hash + 24h auth_date verify orqali ta'minlanadi.</p>
 *
 * <p>Muvaffaqiyatda {@code login-callback-success} sahifasi JWT'ni
 * localStorage'ga saqlab dashboard'ga redirect qiladi; xatoda
 * {@code login-callback-error} xabar ko'rsatib login sahifaga qaytaradi.</p>
 */
@Controller
@RequestMapping("/web/login")
public class TelegramLoginCallbackController {

    private final TelegramLoginService loginService;

    public TelegramLoginCallbackController(TelegramLoginService loginService) {
        this.loginService = loginService;
    }

    @GetMapping("/telegram-callback")
    public String handleCallback(
            @RequestParam Long id,
            @RequestParam("first_name") String firstName,
            @RequestParam(value = "last_name", required = false) String lastName,
            @RequestParam(required = false) String username,
            @RequestParam(value = "photo_url", required = false) String photoUrl,
            @RequestParam("auth_date") Long authDate,
            @RequestParam String hash,
            Model model) {
        try {
            TelegramLoginPayload payload = new TelegramLoginPayload(
                    id, firstName, lastName, username, photoUrl, authDate, hash);
            String jwt = loginService.authenticate(payload);
            String displayName = firstName + (lastName != null ? " " + lastName : "");
            model.addAttribute("jwt", jwt);
            model.addAttribute("displayName", displayName);
            return "web/login-callback-success";
        } catch (TelegramLoginException ex) {
            model.addAttribute("error", ex.getMessage());
            return "web/login-callback-error";
        }
    }
}
