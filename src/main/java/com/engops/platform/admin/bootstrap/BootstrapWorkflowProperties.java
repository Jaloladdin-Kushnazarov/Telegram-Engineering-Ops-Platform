package com.engops.platform.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 156 — first-admin bootstrap'i bilan birga ishga tushirish uchun
 * default MVP Bug workflow seed konfiguratsiyasi.
 *
 * <p>Property prefix: {@code app.bootstrap.workflow}</p>
 *
 * <p>Default {@link #enabled} = false — bootstrap admin yoqilgan bo'lsa ham
 * workflow seed ataylab yoqilishi shart. Yoqilganda
 * {@link BootstrapAdminInitializer} aktiv tenant uchun MVP Bug Flow workflow
 * definition'ini, 4 ta status'ni (BUGS / PROCESSING / TESTING / FIXED) va
 * 5 ta transition rule'ni idempotent ravishda yaratadi.</p>
 *
 * <p>Idempotensiya: faqat workflow definition mavjud bo'lmagan {@code BUG}
 * work-item turi uchun yangi yaratiladi. Mavjud bo'lsa qayta ishlatiladi va
 * faqat yetishmagan status'lar va transition rule'lar to'ldiriladi —
 * duplicate'lar yaratilmaydi.</p>
 *
 * <p>Workflow seed faqat bootstrap admin yoqilgan va muvaffaqiyatli o'tgan
 * sharoitda ishlaydi (admin transaction ichida). Agar bootstrap admin
 * o'chirilgan bo'lsa, bu property ham e'tiborga olinmaydi (no-op).</p>
 */
@ConfigurationProperties("app.bootstrap.workflow")
public class BootstrapWorkflowProperties {

    /** Default false — workflow seed ataylab yoqilishi shart. */
    private boolean enabled = false;

    /**
     * Yaratilayotgan workflow definition nomi.
     * <p>Idempotency'ga ta'siri yo'q (workflow {@code work_item_type=BUG}
     * bo'yicha qidiriladi); faqat yangi yaratilgan rowning {@code name}
     * maydoniga yoziladi. UNIQUE constraint: (tenant_id, name).</p>
     */
    private String name = "MVP Bug Flow";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
