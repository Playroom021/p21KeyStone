import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { postLoginPath } from '../auth/roles';

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? null;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Already signed in (e.g. typed /login by hand): go to the right home.
  if (user) return <Navigate to={postLoginPath(user.role, from)} replace />;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const signedIn = await login(email.trim(), password);
      navigate(postLoginPath(signedIn.role, from), { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, 'Login failed'));
      setSubmitting(false);
    }
  }

  return (
    <div className="center-screen">
      <form className="card login-card" onSubmit={handleSubmit} noValidate>
        <h1>KEYSTONE</h1>
        <p className="muted">Sign in to continue</p>

        {error && (
          <div className="alert" role="alert">
            {error}
          </div>
        )}

        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <button type="submit" className="btn" disabled={submitting || !email.trim() || !password}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
