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
})();
