import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ROLE_LABEL, postLoginPath } from '../auth/roles';
import PasswordInput from '../components/PasswordInput';
import { Alert, Field } from '../components/ui';
import { buildSignupPayload, hasErrors, mapSignupError, validateSignup } from '../lib/validation';
import type { FieldErrors, SignupForm } from '../lib/validation';
import { ROLES } from '../types/auth';

const EMPTY: SignupForm = {
  fullName: '',
  email: '',
  password: '',
  confirmPassword: '',
  role: '',
  companyName: '',
};

export default function SignUpPage() {
  const { user, signup } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState<SignupForm>(EMPTY);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Already signed in: nothing to sign up for.
  if (user) return <Navigate to={postLoginPath(user.role)} replace />;

  const set = (key: keyof SignupForm) => (value: string) => {
    setForm((f) => ({ ...f, [key]: value }));
    // Editing a field clears its own (possibly backend) error.
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (submitting) return;
    setError(null);
    const found = validateSignup(form);
    setErrors(found);
    if (hasErrors(found)) return;

    setSubmitting(true);
    try {
      const created = await signup(buildSignupPayload(form));
      navigate(postLoginPath(created.role), { replace: true });
    } catch (err) {
      const mapped = mapSignupError(getErrorMessage(err, 'Sign up failed'));
      setErrors(mapped.fieldErrors);
      setError(mapped.banner);
      setSubmitting(false);
    }
  }

  return (
    <div className="center-screen">
      <form className="card auth-card" onSubmit={handleSubmit} noValidate>
        <h1>KEYSTONE</h1>
        <p className="muted">Create your account</p>

        {error && <Alert>{error}</Alert>}

        <Field label="Full name" error={errors.fullName}>
          {(p) => (
            <input {...p} type="text" autoComplete="name" value={form.fullName} onChange={(e) => set('fullName')(e.target.value)} />
          )}
        </Field>

        <Field label="Email" error={errors.email}>
          {(p) => (
            <input {...p} type="email" autoComplete="email" value={form.email} onChange={(e) => set('email')(e.target.value)} />
          )}
        </Field>

        <div className="auth-grid">
          <Field label="Password" error={errors.password} hint={errors.password ? undefined : 'At least 6 characters'}>
            {(p) => (
              <PasswordInput {...p} autoComplete="new-password" value={form.password} onChange={(e) => set('password')(e.target.value)} />
            )}
          </Field>
          <Field label="Confirm password" error={errors.confirmPassword}>
            {(p) => (
              <PasswordInput
                {...p}
                autoComplete="new-password"
                value={form.confirmPassword}
                onChange={(e) => set('confirmPassword')(e.target.value)}
              />
            )}
          </Field>
        </div>

        <Field label="Role" error={errors.role}>
          {(p) => (
            <select {...p} value={form.role} onChange={(e) => set('role')(e.target.value)}>
              <option value="">Select a role…</option>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABEL[r]}
                </option>
              ))}
            </select>
          )}
        </Field>

        {form.role === 'CUSTOMER' && (
          <Field label="Company name" error={errors.companyName}>
            {(p) => (
              <input
                {...p}
                type="text"
                autoComplete="organization"
                value={form.companyName}
                onChange={(e) => set('companyName')(e.target.value)}
              />
            )}
          </Field>
        )}

        <button type="submit" className="btn btn-block" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Sign up'}
        </button>

        <p className="auth-switch">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </div>
  );
}
