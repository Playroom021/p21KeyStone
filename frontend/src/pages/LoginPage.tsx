import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { getErrorMessage, getErrorStatus } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { postLoginPath } from '../auth/roles';
import PasswordInput from '../components/PasswordInput';
import { Alert, Field } from '../components/ui';
import { hasErrors, validateLogin } from '../lib/validation';
import type { FieldErrors } from '../lib/validation';

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? null;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Already signed in (e.g. typed /login by hand): go to the right home.
  if (user) return <Navigate to={postLoginPath(user.role, from)} replace />;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (submitting) return;
    setError(null);
    const found = validateLogin({ email, password });
    setErrors(found);
    if (hasErrors(found)) return;

    setSubmitting(true);
    try {
      const signedIn = await login(email.trim(), password);
      navigate(postLoginPath(signedIn.role, from), { replace: true });
    } catch (err) {
      // 401 = wrong email/password; the backend message is already user-safe.
      setError(getErrorMessage(err, getErrorStatus(err) === 401 ? 'Invalid email or password' : 'Login failed'));
      setSubmitting(false);
    }
  }

  return (
    <div className="center-screen">
      <form className="card login-card auth-card" onSubmit={handleSubmit} noValidate>
        <h1>KEYSTONE</h1>
        <p className="muted">Sign in to continue</p>

        {error && <Alert>{error}</Alert>}

        <Field label="Email" error={errors.email}>
          {(p) => (
            <input
              {...p}
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
        </Field>

        <Field label="Password" error={errors.password}>
          {(p) => (
            <PasswordInput
              {...p}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          )}
        </Field>

        <button type="submit" className="btn btn-block" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="auth-switch">
          New to KEYSTONE? <Link to="/signup">Create an account</Link>
        </p>
      </form>
    </div>
  );
}
