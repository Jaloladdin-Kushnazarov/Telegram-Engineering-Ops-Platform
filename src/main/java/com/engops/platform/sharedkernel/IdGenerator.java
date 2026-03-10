package com.engops.platform.sharedkernel;

import java.util.UUID;

/**
 * Platformada yagona (unique) identifikator yaratish uchun yordamchi klass.
 *
 * Barcha modullar yangi ID kerak bo'lganda shu klass orqali yaratadi.
 * Hozircha UUID v4 ishlatiladi. Kelajakda kerak bo'lsa,
 * boshqa strategiyaga o'tish oson — faqat shu klassni o'zgartirish yetarli.
 */
public final class IdGenerator {

    private IdGenerator() {
        // Instance yaratishni oldini oladi — faqat statik metodlar ishlatiladi
    }

    /**
     * Yangi UUID formatidagi identifikator yaratadi.
     *
     * @return tasodifiy UUID string ko'rinishida
     */
    public static String newId() {
        return UUID.randomUUID().toString();
    }
}