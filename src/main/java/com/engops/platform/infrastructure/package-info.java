/**
 * Infrastructure moduli.
 *
 * Bu modul platformaning texnik infratuzilma qismlarini boshqaradi:
 * - Web filterlari (correlation-id, xavfsizlik)
 * - Markaziy xatolik qayta ishlash (GlobalExceptionHandler)
 * - JSON/Jackson konfiguratsiyasi
 * - JPA auditing konfiguratsiyasi
 *
 * Bu modul shared-kernel'dan foydalanishi mumkin,
 * lekin boshqa biznes modullariga to'g'ridan-to'g'ri bog'lanmasligi kerak.
 */
package com.engops.platform.infrastructure;
