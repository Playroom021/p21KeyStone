import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { NAV_ITEMS } from '../auth/nav';
import { ROLE_LABEL } from '../auth/roles';

/** Header + role navigation + page area shared by every signed-in page. */
export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);

  async function handleLogout() {
    setBusy(true);
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <strong className="brand">KEYSTONE</strong>
        {user && (
          <div className="user-box">
            <span className="user-name">
              {user.fullName} <span className="badge">{ROLE_LABEL[user.role]}</span>
            </span>
            <button type="button" className="btn btn-secondary" onClick={handleLogout} disabled={busy}>
              {busy ? 'Signing out…' : 'Log out'}
            </button>
          </div>
        )}
      </header>
      {user && NAV_ITEMS[user.role].length > 1 && (
        <nav className="app-nav" aria-label="Main">
          {NAV_ITEMS[user.role].map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      )}
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
