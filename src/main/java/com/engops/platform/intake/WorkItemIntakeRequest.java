package com.engops.platform.intake;

import java.util.UUID;

/**
 * Work item intake uchun HTTP request DTO.
 *
 * Field'lar IntakeCommand'ga to'g'ridan-to'g'ri map qilinadi. Validation
 * IntakeApplicationService.validateCommand(...) tomonidan bajariladi —
 * controller faqat thin adapter, boshqa biznes validatsiyasini qo'shmaydi.
 *
 * @param tenantId tenant identifikatori (required)
 * @param typeCode work item turi: BUG, INCIDENT, TASK (required, controller'da
 *                  WorkItemType enum'ga konvertatsiya qilinadi)
 * @param title work item sarlavhasi (required, non-blank)
 * @param description ixtiyoriy tavsif (nullable)
 * @param workflowDefinitionId aniq workflow definition (nullable; agar berilmasa,
 *                              IntakeApplicationService active workflow'ni avtomatik tanlaydi)
 * @param initialStatusCode aniq boshlang'ich status (nullable; berilmasa,
 *                           workflow definition'dagi initial=true status avtomatik tanlanadi)
 * @param createdByUserId yaratuvchi foydalanuvchi (required)
 * @param actionSource amal manbai: MANUAL, TELEGRAM, SYSTEM va h.k. (required)
 */
public record WorkItemIntakeRequest(
        UUID tenantId,
        String typeCode,
        String title,
        String description,
        UUID workflowDefinitionId,
        String initialStatusCode,
        UUID createdByUserId,
        String actionSource) {}
