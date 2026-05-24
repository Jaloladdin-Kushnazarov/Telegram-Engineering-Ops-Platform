/* Phase 208 — localStorage-based JWT lifecycle for browser-side auth.
   Vanilla ES2017+, no build step, no framework. Loaded as <script defer>
   in base.html. */
(function() {
    const JWT_KEY = 'platform.jwt';

    function getJwt() { return localStorage.getItem(JWT_KEY); }
    function setJwt(value) { localStorage.setItem(JWT_KEY, value); }
    function clearJwt() {
        localStorage.removeItem(JWT_KEY);
        window.location.href = '/web/login';
    }

    // Expose globally for inline onclick handlers + login page submit handler.
    window.clearJwt = clearJwt;
    window.getJwt = getJwt;
    window.setJwt = setJwt;

    // Toggle login/logout link visibility based on JWT presence.
    document.addEventListener('DOMContentLoaded', function() {
        const loginLink = document.getElementById('login-link');
        const logoutLink = document.getElementById('logout-link');
        if (getJwt()) {
            if (loginLink) loginLink.style.display = 'none';
            if (logoutLink) logoutLink.style.display = '';
        }
    });

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

    // ===== Phase 218b: Telegram Login Widget callback =====
    //
    // Telegram widget'ning data-onauth atribut'i window.onTelegramAuth
    // chaqiradi. User payload (id, first_name, last_name, username,
    // photo_url, auth_date, hash) backend'ga JSON sifatida yuboriladi —
    // Telegram'ning snake_case'ini camelCase'ga aylantirib (Phase 218a
    // TelegramLoginPayload record talab qiladi).
    window.onTelegramAuth = function(user) {
        fetch('/api/auth/telegram-login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                id: user.id,
                firstName: user.first_name,
                lastName: user.last_name || null,
                username: user.username || null,
                photoUrl: user.photo_url || null,
                authDate: user.auth_date,
                hash: user.hash
            })
        }).then(function(response) {
            if (!response.ok) {
                return response.json().then(function(body) {
                    throw new Error(body && body.error
                            ? body.error
                            : 'HTTP ' + response.status);
                });
            }
            return response.json();
        }).then(function(data) {
            if (data && data.token) {
                setJwt(data.token);
                window.location.href = '/web/dashboard';
            } else {
                throw new Error('Token javob ichida yo\'q');
            }
        }).catch(function(error) {
            var msg = document.getElementById('login-message');
            if (msg) {
                msg.textContent = 'Telegram login xatolik: ' + error.message;
            } else {
                console.error('Telegram login error:', error);
            }
        });
    };

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
        fetch('/web/api/platform/tenants', {
            method: 'GET',
            headers: { 'Authorization': 'Bearer ' + jwt }
        }).then(function(response) {
            if (response.ok) {
                link.style.display = '';
            }
        }).catch(function() { /* silent — link yashirinligicha qoladi */ });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', detectPlatformOwner);
    } else {
        detectPlatformOwner();
    }
})();
