package com.engops.platform.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON serialization va deserialization konfiguratsiyasi.
 *
 * Bu klass platformadagi barcha JSON operatsiyalari uchun
 * yagona ObjectMapper sozlamalarini belgilaydi.
 *
 * Asosiy sozlamalar:
 * - Sana/vaqt ISO-8601 formatida yoziladi (masalan: "2026-03-10T12:00:00Z")
 * - Noma'lum JSON maydonlari xatolikka olib kelmaydi (yangi versiyalar bilan moslashuvchanlik)
 * - Java 8+ vaqt turlari (Instant, LocalDate) to'g'ri qayta ishlanadi
 */
@Configuration
public class JacksonConfig {

    /**
     * Platformaning asosiy ObjectMapper bean'ini yaratadi.
     *
     * @return sozlangan ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java 8+ vaqt turlari (Instant, LocalDateTime) uchun modul
        mapper.registerModule(new JavaTimeModule());

        // Sana/vaqtni timestamp (raqam) o'rniga ISO-8601 string sifatida yozadi
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // JSON'da entity'da yo'q maydon kelsa — xatolik bermasin
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}