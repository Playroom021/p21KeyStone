const TOKEN_KEY = 'authapp_token';
const USER_KEY = 'authapp_user';

function saveSession(authResponse) {
    localStorage.setItem(TOKEN_KEY, authResponse.token);
    localStorage.setItem(USER_KEY, JSON.stringify({
        fullName: authResponse.fullName,
        email: authResponse.email,
        role: authResponse.role
    }));
}

function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

function getStoredUser() {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
}

function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
}

function requireAuthOrRedirect() {
    if (!getToken()) {
        window.location.href = '/login.html';
        return false;
    }
    return true;
}

async function authFetch(url, options = {}) {
    const headers = Object.assign({}, options.headers || {}, {
        'Authorization': 'Bearer ' + getToken()
    });
    const res = await fetch(url, Object.assign({}, options, { headers }));
    if (res.status === 401) {
        clearSession();
        window.location.href = '/login.html';
        throw new Error('Session expired');
    }
    return res;
}

async function logout() {
    try {
        await fetch('/api/auth/logout', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + getToken() }
        });
    } catch (e) {
        // even if the network call fails, still clear the local session
    }
    clearSession();
    window.location.href = '/login.html';
}

function roleLabel(role) {
    const labels = {
        MANAGER: 'Manager',
        LOCAL_CUSTOMER: 'Local Customer',
        LOCAL_WORKER: 'Local Worker',
        DEVELOPER: 'Developer'
    };
    return labels[role] || role;
}

function homeUrlForRole(role) {
    return role === 'LOCAL_CUSTOMER' ? '/customer-portal.html' : '/dashboard.html';
}
