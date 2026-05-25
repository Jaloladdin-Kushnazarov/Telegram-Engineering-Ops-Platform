/* Phase 208 — localStorage-based JWT lifecycle for browser-side auth.
   Vanilla ES2017+, no build step, no framework. Loaded as <script defer>
   in base.html. */
(function() {
    const JWT_KEY = 'platform.jwt';

    const USER_DISPLAY_NAME_KEY = 'platform.userDisplayName';

    // Phase 219c — nav link visibility cache (flash'ni yo'q qilish uchun).
    // Async probe natijasi shu yerda saqlanadi; keyingi page load'da link
    // DARHOL ko'rsatiladi va probe fonda cache'ni yangilaydi/invalidate qiladi.
    const NAV_CACHE_PLATFORM_OWNER = 'platform.navCache.platformOwner';
    const NAV_CACHE_MEMBER_MANAGER = 'platform.navCache.memberManager';

    function clearNavCache() {
        localStorage.removeItem(NAV_CACHE_PLATFORM_OWNER);
        localStorage.removeItem(NAV_CACHE_MEMBER_MANAGER);
    }

    function getJwt() { return localStorage.getItem(JWT_KEY); }
    function setJwt(value) { localStorage.setItem(JWT_KEY, value); }
    function clearJwt() {
        // Phase 218c — barcha session keylarni tozalaymiz (JWT, user
        // display name, faollashtirilgan tenant). Logout butun sessiya
        // qoldiqlarini olib tashlasin.
        localStorage.removeItem(JWT_KEY);
        localStorage.removeItem(USER_DISPLAY_NAME_KEY);
        localStorage.removeItem('platform.tenantId');
        clearNavCache();  // Phase 219c — security invariant: nav cache'ni ham tozalash
        window.location.href = '/web/login';
    }

    // Phase 218c — login/logout link + user display visibility ni JWT
    // mavjudligi asosida qayta hisoblaydi. DOMContentLoaded'da ham,
    // login muvaffaqiyatidan keyin ham qayta chaqiriladi (window
    // orqali expose qilinganligi sababli).
    function updateAuthNav() {
        const loginLink = document.getElementById('login-link');
        const logoutLink = document.getElementById('logout-link');
        const userDisplay = document.getElementById('user-display');
        if (getJwt()) {
            if (loginLink) loginLink.style.display = 'none';
            if (logoutLink) logoutLink.style.display = '';
            const displayName = localStorage.getItem(USER_DISPLAY_NAME_KEY);
            if (userDisplay) {
                if (displayName) {
                    userDisplay.style.display = '';
                    userDisplay.textContent = displayName;
                } else {
                    userDisplay.style.display = 'none';
                }
            }
        } else {
            if (loginLink) loginLink.style.display = '';
            if (logoutLink) logoutLink.style.display = 'none';
            if (userDisplay) userDisplay.style.display = 'none';
        }
    }

    // Expose globally for inline onclick handlers + login page submit handler.
    window.clearJwt = clearJwt;
    window.getJwt = getJwt;
    window.setJwt = setJwt;
    window.updateAuthNav = updateAuthNav;

    document.addEventListener('DOMContentLoaded', updateAuthNav);

    // Auto-attach Authorization header to HTMX requests so /api/** calls
    // from HTMX-driven page fragments authenticate via the stored JWT.
    document.body.addEventListener('htmx:configRequest', function(e) {
        const jwt = getJwt();
        if (jwt) {
            e.detail.headers['Authorization'] = 'Bearer ' + jwt;
        }
    });

    // ===== Phase 210: tenant persistence + selector handler =====
    const TENANT_KEY = 'platform.tenantId';

    function getActiveTenant() { return localStorage.getItem(TENANT_KEY); }
    function setActiveTenant(uuid) {
        if (!uuid) return;
        localStorage.setItem(TENANT_KEY, uuid);
    }
    function onTenantSelected(uuid) {
        if (!uuid) return;
        setActiveTenant(uuid);
        const url = new URL(window.location.href);
        url.searchParams.set('tenantId', uuid);
        window.location.href = url.toString();
    }

    window.getActiveTenant = getActiveTenant;
    window.setActiveTenant = setActiveTenant;
    window.onTenantSelected = onTenantSelected;

    // On dashboard / work-items pages, if URL lacks ?tenantId but
    // localStorage has one, transparently redirect with the saved id.
    document.addEventListener('DOMContentLoaded', function() {
        const tenantAwarePaths = ['/web/dashboard', '/web/work-items'];
        const path = window.location.pathname;
        if (tenantAwarePaths.indexOf(path) === -1) return;
        const params = new URLSearchParams(window.location.search);
        if (params.get('tenantId')) return;
        const saved = getActiveTenant();
        if (!saved) return;
        params.set('tenantId', saved);
        window.location.replace(path + '?' + params.toString());
    });

    // ===== Phase 218d: Telegram Login Widget REDIRECT MODE =====
    //
    // Phase 218b/c callback mode (data-onauth JS callback + iframe
    // postMessage) olib tashlandi: ngrok orqali cross-origin postMessage
    // ishonchsiz edi va JWT hech qachon saqlanmasdi. Endi widget
    // data-auth-url bilan to'g'ridan-to'g'ri /web/login/telegram-callback'ga
    // redirect qiladi — JWT saqlash server tomonidan qaytarilgan
    // login-callback-success.html inline script'ida bajariladi. Bu yerda
    // hech qanday JS callback kerak emas.

    // ===== Phase 217b: detect PLATFORM_OWNER + show Platform nav link =====
    //
    // Strategy — async probe. JWT claim decode JS'da qilinmaydi (browser
    // tokenni ishonchli decode qila olmaydi: signature verify yo'q,
    // payload schema'ni qo'lda parse qilish risk). O'rniga server-side
    // PLATFORM_TENANT_LIST tekshiruvini qaytadan ishlatamiz —
    // GET /web/api/platform/tenants 200 → ko'rsat, 403/4xx → yashir.
    function detectPlatformOwner() {
        const link = document.getElementById('nav-link-platform');
        if (!link) return;
        const jwt = getJwt();
        if (!jwt) return;

        // Phase 219c — cache'dan darhol ko'rsatish (flash yo'q).
        if (localStorage.getItem(NAV_CACHE_PLATFORM_OWNER) === 'true') {
            link.style.display = '';
        }

        // Async probe — cache'ni yangilash yoki invalidate qilish.
        fetch('/web/api/platform/tenants', {
            method: 'GET',
            headers: { 'Authorization': 'Bearer ' + jwt }
        }).then(function(response) {
            if (response.ok) {
                localStorage.setItem(NAV_CACHE_PLATFORM_OWNER, 'true');
                link.style.display = '';
            } else {
                localStorage.removeItem(NAV_CACHE_PLATFORM_OWNER);
                link.style.display = 'none';
            }
        }).catch(function() { /* silent — link yashirinligicha qoladi */ });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', detectPlatformOwner);
    } else {
        detectPlatformOwner();
    }

    // ===== Phase 219b — show "Members" nav link if MEMBER_MANAGE in tenant =====
    //
    // Tenant-scoped probe (detectPlatformOwner'dan farqli — u global).
    // tenantId URL query param (Phase 210) yoki localStorage'dan olinadi.
    // /web/api/tenants/{tenantId}/members denied holatda ham 200 qaytaradi
    // (HTMX swap UX), shuning uchun response.ok yetarli emas — body ichidagi
    // 'denied-state' marker tekshiriladi: bo'lmasa = MEMBER_MANAGE bor.
    function detectMemberManager() {
        const link = document.getElementById('nav-link-members');
        if (!link) return;
        const jwt = getJwt();
        if (!jwt) return;

        // Tenant resolve: URL query (Phase 210) → localStorage → BO'SH bo'lsa
        // link yashirin (members context tenant ichida ma'noli).
        const urlParams = new URLSearchParams(window.location.search);
        const tenantId = urlParams.get('tenantId') || getActiveTenant();
        if (!tenantId) {
            link.style.display = 'none';
            return;
        }

        // Phase 219c — href'ni dynamically set qilish (base.html endi
        // tenantId=null sahifalarda ham elementni render qiladi, lekin href="#").
        link.setAttribute('href', '/web/tenants/' + tenantId + '/members');

        // Cache'dan darhol ko'rsatish (flash yo'q).
        if (localStorage.getItem(NAV_CACHE_MEMBER_MANAGER) === 'true') {
            link.style.display = '';
        }

        // Async probe — denied holatda ham 200 qaytadi, shuning uchun body
        // ichidagi 'denied-state' marker tekshiriladi.
        fetch('/web/api/tenants/' + tenantId + '/members', {
            method: 'GET',
            headers: { 'Authorization': 'Bearer ' + jwt }
        }).then(function(response) {
            if (!response.ok) {
                localStorage.removeItem(NAV_CACHE_MEMBER_MANAGER);
                link.style.display = 'none';
                return;
            }
            return response.text().then(function(html) {
                if (html.indexOf('denied-state') === -1) {
                    localStorage.setItem(NAV_CACHE_MEMBER_MANAGER, 'true');
                    link.style.display = '';
                } else {
                    localStorage.removeItem(NAV_CACHE_MEMBER_MANAGER);
                    link.style.display = 'none';
                }
            });
        }).catch(function() { /* silent — link yashirinligicha qoladi */ });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', detectMemberManager);
    } else {
        detectMemberManager();
    }
})();
