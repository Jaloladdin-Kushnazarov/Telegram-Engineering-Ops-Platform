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
})();
