package com.engops.platform.admin;

/**
 * Chat binding PATCH yangilash uchun HTTP request DTO.
 *
 * PATCH semantikasi: faqat JSON'da mavjud bo'lgan field'lar yangilanadi.
 * - chatTitle berilmasa — mavjud sarlavha saqlanadi
 * - chatTitle explicitly null/blank berilsa — sarlavha tozalanadi
 * - bindingType berilmasa — mavjud tur saqlanadi
 * - bindingType berilsa — faqat MAIN_GROUP yoki NOTIFICATION_GROUP
 *
 * Jackson faqat JSON'da mavjud field'lar uchun setter chaqiradi,
 * shuning uchun provided flag'lar orqali omitted vs explicit null farqlanadi.
 */
public class UpdateChatBindingRequest {

    private String chatTitle;
    private boolean chatTitleProvided;

    private String bindingType;
    private boolean bindingTypeProvided;

    public UpdateChatBindingRequest() {}

    public String getChatTitle() { return chatTitle; }
    public void setChatTitle(String chatTitle) {
        this.chatTitle = chatTitle;
        this.chatTitleProvided = true;
    }

    public String getBindingType() { return bindingType; }
    public void setBindingType(String bindingType) {
        this.bindingType = bindingType;
        this.bindingTypeProvided = true;
    }

    public boolean isChatTitleProvided() { return chatTitleProvided; }
    public boolean isBindingTypeProvided() { return bindingTypeProvided; }
}
