/**
 * Shared Kernel moduli.
 *
 * Bu modul barcha boshqa modullar tomonidan ishlatiladigan umumiy abstraction'larni saqlaydi:
 * - ID value object'lar (TenantId, UserId)
 * - Bazaviy entity class'lar (BaseEntity, AuditableEntity)
 * - Platformaga xos exception'lar
 * - ID generatsiya utility'lari
 *
 * Muhim qoidalar:
 * - Bu modul imkon qadar kichik bo'lishi kerak
 * - Faqat haqiqatan ham umumiy bo'lgan narsalar bu yerga qo'yiladi
 * - Biznes logikasi bu modulga tegishli emas
 * - Bu modul boshqa hech qanday modulga bog'liq bo'lmasligi kerak
 */
package com.engops.platform.sharedkernel;