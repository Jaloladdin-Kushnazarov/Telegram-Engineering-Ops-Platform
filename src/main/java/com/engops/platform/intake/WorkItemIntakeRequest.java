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
 * @param priorityCode Phase 195 — ixtiyoriy ustuvorlik kodi (LOW / MEDIUM /
 *                     HIGH / CRITICAL). Null yoki bo'sh string null sifatida
 *                     qaraladi. Service layer bounded enum validatsiyasi
 *                     authoritative.
 * @param severityCode Phase 195 — ixtiyoriy jiddiylik kodi (LOW / MEDIUM /
 *                     HIGH / CRITICAL). Null yoki bo'sh string null sifatida
 *                     qaraladi.
 * @param ownerUserId Phase 195 — ixtiyoriy boshlang'ich egasi (AppUser id).
 *                    Null bo'lsa o'rnatilmaydi. Non-null bo'lsa, intake actor'i
 *                    kabi shu tenantda ACTIVE membership ga ega bo'lishi shart.
 */
public record WorkItemIntakeRequest(
        UUID tenantId,
        String typeCode,
        String title,
        String description,
        UUID workflowDefinitionId,
        String initialStatusCode,
        UUID createdByUserId,
        String actionSource,
        String priorityCode,
        String severityCode,
        UUID ownerUserId) {}
