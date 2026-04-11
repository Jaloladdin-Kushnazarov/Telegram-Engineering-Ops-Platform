package com.engops.platform.admin;

/**
 * Topic binding PATCH yangilash uchun HTTP request DTO.
 *
 * PATCH semantikasi: faqat JSON'da mavjud bo'lgan field'lar yangilanadi.
 * - topicName berilmasa — mavjud nom saqlanadi
 * - topicName explicitly null/blank berilsa — nom tozalanadi
 *
 * Model ichida faqat topicName mutable metadata — topicId, purpose va parent chat binding
 * immutable hisoblanadi.
 *
 * Jackson faqat JSON'da mavjud field'lar uchun setter chaqiradi,
 * shuning uchun provided flag'lar orqali omitted vs explicit null farqlanadi.
 */
public class UpdateTopicBindingRequest {

    private String topicName;
    private boolean topicNameProvided;

    public UpdateTopicBindingRequest() {}

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) {
        this.topicName = topicName;
        this.topicNameProvided = true;
    }

    public boolean isTopicNameProvided() { return topicNameProvided; }
}
