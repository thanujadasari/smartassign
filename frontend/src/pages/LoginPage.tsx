import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Lock, Mail } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { Alert } from '@/components/ui/misc';
import { HOME_FOR, useAuth } from '@/lib/auth';
import { toApiError } from '@/lib/api';
import type { Role } from '@/lib/types';
import { AuthLayout } from './auth/AuthLayout';

/** A deep link is only honoured if it belongs to the signed-in user's own portal. */
export function landingFor(role: Role, from?: string) {
  const home = HOME_FOR[role];
  if (!from || from === '/login') return home;
  const inPortal = role === 'SUPERVISOR'
    ? !from.startsWith('/customer') && !from.startsWith('/handler')
    : from === home || from.startsWith(home + '/');
  return inPortal ? from : home;
}

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);
  const state = location.state as { from?: string; registered?: string } | null;

  if (user) return <Navigate to={landingFor(user.role, state?.from)} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!email.trim() || !password) return;
    setPending(true);
    setError(null);
    try {
      const u = await login(email.trim(), password);
      navigate(landingFor(u.role, state?.from), { replace: true });
    } catch (err) {
      const apiErr = toApiError(err);
      setError(apiErr.status === 401 ? 'The email or password is incorrect.' : apiErr.message);
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Use the email address you registered with."
      footer={
        <div>
          <p>New customer? <Link to="/signup" className="font-semibold text-accent-600 hover:underline">Create an account</Link></p>
          <p>Supervisor or handler? <Link to="/staff-signup" className="font-semibold text-accent-600 hover:underline">Staff sign-up</Link></p>
        </div>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        {state?.registered && <Alert tone="success">Account created for {state.registered}. Please sign in.</Alert>}
        {error && <Alert tone="danger">{error}</Alert>}
        <Field label="Email" htmlFor="email" required error={touched && !email.trim() ? 'Enter your email' : undefined}>
          <div className="relative">
            <Mail className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-ink-subtle" />
            <Input id="email" type="email" autoComplete="email" autoFocus placeholder="you@example.com" value={email} className="pl-10"
              invalid={touched && !email.trim()} onChange={(e) => setEmail(e.target.value)} />
          </div>
        </Field>
        <Field label="Password" htmlFor="password" required error={touched && !password ? 'Enter your password' : undefined}>
          <div className="relative">
            <Lock className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-ink-subtle" />
            <Input id="password" type={show ? 'text' : 'password'} autoComplete="current-password" value={password}
              placeholder="Password" className="px-10" invalid={touched && !password} onChange={(e) => setPassword(e.target.value)} />
            <button type="button" onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 cursor-pointer text-ink-subtle hover:text-ink">
              {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            </button>
          </div>
        </Field>
        <Button type="submit" size="lg" className="w-full" loading={pending}>Sign in</Button>
      </form>
    </AuthLayout>
  );
}
