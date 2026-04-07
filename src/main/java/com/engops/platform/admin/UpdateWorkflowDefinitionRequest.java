package com.engops.platform.admin;

/**
 * Workflow definition PATCH yangilash uchun HTTP request DTO.
 *
 * PATCH semantikasi: faqat JSON'da mavjud bo'lgan field'lar yangilanadi.
 * - name berilmasa — mavjud nom saqlanadi
 * - description berilmasa — mavjud tavsif saqlanadi
 * - description explicitly null/blank berilsa — tavsif tozalanadi
 *
 * Jackson faqat JSON'da mavjud field'lar uchun setter chaqiradi,
 * shuning uchun provided flag'lar orqali omitted vs explicit null farqlanadi.
 */
public class UpdateWorkflowDefinitionRequest {

    private String name;
    private boolean nameProvided;

    private String description;
    private boolean descriptionProvided;

    public UpdateWorkflowDefinitionRequest() {}

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    public boolean isNameProvided() { return nameProvided; }
    public boolean isDescriptionProvided() { return descriptionProvided; }
}
