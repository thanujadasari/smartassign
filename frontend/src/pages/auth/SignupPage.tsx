import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { Alert } from '@/components/ui/misc';
import { api, toApiError, type ApiError } from '@/lib/api';
import { AuthLayout } from './AuthLayout';

export interface AccountFields { fullName: string; email: string; phone: string; password: string; confirm: string }

export function validateAccount(f: AccountFields): Partial<Record<keyof AccountFields, string>> {
  const e: Partial<Record<keyof AccountFields, string>> = {};
  if (!f.fullName.trim()) e.fullName = 'Enter your full name';
  if (!/^\S+@\S+\.\S+$/.test(f.email.trim())) e.email = 'Enter a valid email address';
  if (f.phone.trim() && !/^[0-9+()\- ]{7,30}$/.test(f.phone.trim())) e.phone = 'Enter a valid phone number';
  if (f.password.length < 10) e.password = 'Use at least 10 characters';
  if (f.confirm !== f.password) e.confirm = 'Passwords do not match';
  return e;
}

/** Name, email, phone and password - shared by customer and staff sign-up. */
export function AccountFieldset({ f, set, errors, server }: {
  f: AccountFields; set: (k: keyof AccountFields, v: string) => void;
  errors: Partial<Record<keyof AccountFields, string>>; server: ApiError | null;
}) {
  const err = (k: keyof AccountFields) => errors[k] ?? server?.fieldErrors[k];
  return (
    <>
      <Field label="Full name" htmlFor="fullName" required error={err('fullName')}>
        <Input id="fullName" autoComplete="name" value={f.fullName} invalid={!!err('fullName')} onChange={(e) => set('fullName', e.target.value)} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Email" htmlFor="email" required error={err('email')}>
          <Input id="email" type="email" autoComplete="email" placeholder="you@example.com" value={f.email} invalid={!!err('email')} onChange={(e) => set('email', e.target.value)} />
        </Field>
        <Field label="Phone" htmlFor="phone" error={err('phone')} hint="Optional">
          <Input id="phone" type="tel" autoComplete="tel" placeholder="+91" value={f.phone} invalid={!!err('phone')} onChange={(e) => set('phone', e.target.value)} />
        </Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Password" htmlFor="password" required error={err('password')} hint="At least 10 characters">
          <Input id="password" type="password" autoComplete="new-password" value={f.password} invalid={!!err('password')} onChange={(e) => set('password', e.target.value)} />
        </Field>
        <Field label="Confirm password" htmlFor="confirm" required error={err('confirm')}>
          <Input id="confirm" type="password" autoComplete="new-password" value={f.confirm} invalid={!!err('confirm')} onChange={(e) => set('confirm', e.target.value)} />
        </Field>
      </div>
    </>
  );
}

export const EMPTY_ACCOUNT: AccountFields = { fullName: '', email: '', phone: '', password: '', confirm: '' };

export default function SignupPage() {
  const navigate = useNavigate();
  const [f, setF] = useState<AccountFields>(EMPTY_ACCOUNT);
  const [touched, setTouched] = useState(false);
  const [pending, setPending] = useState(false);
  const [server, setServer] = useState<ApiError | null>(null);
  const errors = touched ? validateAccount(f) : {};
  const set = (k: keyof AccountFields, v: string) => setF((p) => ({ ...p, [k]: v }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(validateAccount(f)).length) return;
    setPending(true);
    setServer(null);
    try {
      await api.auth.registerCustomer({ fullName: f.fullName.trim(), email: f.email.trim(), phone: f.phone.trim() || undefined, password: f.password });
      navigate('/login', { replace: true, state: { registered: f.email.trim() } });
    } catch (err) {
      setServer(toApiError(err));
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthLayout wide title="Create your account" subtitle="Register to report and track your claims."
      footer={<p>Already have an account? <Link to="/login" className="font-semibold text-accent-600 hover:underline">Sign in</Link></p>}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {server && <Alert tone="danger">{server.message}</Alert>}
        <AccountFieldset f={f} set={set} errors={errors} server={server} />
        <Button type="submit" size="lg" className="w-full" loading={pending}>Create account</Button>
      </form>
    </AuthLayout>
  );
}
