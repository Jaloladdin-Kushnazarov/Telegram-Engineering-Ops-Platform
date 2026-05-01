package com.engops.platform.infrastructure.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Phase 125 — controller method parameter annotation: joriy
 * {@link AuthenticatedActor}'ni SecurityContext'dan resolve qiladi.
 *
 * <p>Qo'llab-quvvatlanadigan parameter turlari (Phase 125 foundation):</p>
 * <ul>
 *   <li>{@code java.util.UUID} — actor'ning {@code appUserId} qiymati uzatiladi</li>
 *   <li>{@link AuthenticatedActor} — to'liq actor uzatiladi</li>
 * </ul>
 *
 * <p>Hozirda hech bir mavjud controller bu annotatsiyani ishlatmaydi —
 * {@link CurrentActorArgumentResolver} foundation sifatida ro'yxatga olingan,
 * keyingi phase'larda (126+) controller'lar X-Actor-User-Id header va body
 * field'larni shu mexanizmga ko'chiriladi.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentActor {
}
