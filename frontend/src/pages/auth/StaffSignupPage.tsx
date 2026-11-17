import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShieldCheck, Wrench } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Field, Input, Select } from '@/components/ui/input';
import { Alert, Skeleton } from '@/components/ui/misc';
import { api, toApiError, type ApiError } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { useOptions } from '@/lib/options';
import { cn } from '@/lib/utils';
import type { Expertise } from '@/lib/types';
import { AccountFieldset, EMPTY_ACCOUNT, validateAccount, type AccountFields } from './SignupPage';
import { AuthLayout } from './AuthLayout';

type StaffRole = 'SUPERVISOR' | 'HANDLER';

export default function StaffSignupPage() {
  const navigate = useNavigate();
  const options = useOptions();
  const reg = useAsync(() => api.auth.registrationOptions(), []);
  const [role, setRole] = useState<StaffRole>('HANDLER');
  const [f, setF] = useState<AccountFields>(EMPTY_ACCOUNT);
  const [code, setCode] = useState('');
  const [expertise, setExpertise] = useState<Expertise | ''>('');
  const [years, setYears] = useState('');
  const [city, setCity] = useState('');
  const [touched, setTouched] = useState(false);
  const [pending, setPending] = useState(false);
  const [server, setServer] = useState<ApiError | null>(null);
  const set = (k: keyof AccountFields, v: string) => setF((p) => ({ ...p, [k]: v }));

  const extra = () => {
    const e: Record<string, string> = {};
    if (!code.trim()) e.staffCode = 'Enter the staff registration code';
    if (role === 'HANDLER') {
      if (!expertise) e.expertise = 'Choose your primary expertise';
      const y = Number(years);
      if (years === '' || !Number.isInteger(y) || y < 0 || y > 60) e.years = 'Enter whole years between 0 and 60';
      if (!city) e.city = 'Choose your base city';
    }
    return e;
  };
  const errors = touched ? validateAccount(f) : {};
  const extraErrors = touched ? extra() : {};

  const submit = async (ev: FormEvent) => {
    ev.preventDefault();
    setTouched(true);
    if (Object.keys(validateAccount(f)).length || Object.keys(extra()).length) return;
    setPending(true);
    setServer(null);
    try {
      await api.auth.registerStaff({
        fullName: f.fullName.trim(), email: f.email.trim(), phone: f.phone.trim() || undefined, password: f.password,
        role, staffCode: code.trim(),
        handlerProfile: role === 'HANDLER' ? { primaryExpertise: expertise as Expertise, experienceYears: Number(years), location: city } : undefined,
      });
      navigate('/login', { replace: true, state: { registered: f.email.trim() } });
    } catch (err) {
      setServer(toApiError(err));
    } finally {
      setPending(false);
    }
  };

  const disabled = reg.data && !reg.data.staffSignupEnabled;

  return (
    <AuthLayout wide title="Create a staff account" subtitle="For supervisors and claim handlers. You need the staff registration code from your administrator."
      footer={<p>Already have an account? <Link to="/login" className="font-semibold text-accent-600 hover:underline">Sign in</Link></p>}>
      {reg.loading && <Skeleton className="h-40" />}
      {disabled && (
        <Alert tone="warning" title="Staff sign-up is switched off">
          The server has no staff registration code configured (<code>SMARTASSIGN_STAFF_REGISTRATION_CODE</code>). Ask your administrator to set one.
        </Alert>
      )}
      {!reg.loading && !disabled && (
        <form onSubmit={submit} className="space-y-4" noValidate>
          {server && <Alert tone="danger">{server.message}</Alert>}
          <fieldset>
            <legend className="mb-1.5 text-[12.5px] font-medium text-ink">I am a</legend>
            <div className="grid grid-cols-2 gap-2" role="radiogroup">
              {([['HANDLER', 'Claim handler', Wrench], ['SUPERVISOR', 'Supervisor', ShieldCheck]] as const).map(([value, label, Icon]) => (
                <button key={value} type="button" role="radio" aria-checked={role === value} onClick={() => setRole(value)}
                  className={cn('flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2.5 text-left text-[13.5px] font-medium',
                    role === value ? 'border-navy-900 bg-navy-50 text-ink' : 'border-border text-ink-muted hover:border-border-strong')}>
                  <Icon className="size-4" /> {label}
                </button>
              ))}
            </div>
          </fieldset>
          <AccountFieldset f={f} set={set} errors={errors} server={server} />
          {role === 'HANDLER' && (
            <div className="rounded-lg border border-border bg-canvas p-4">
              <p className="mb-3 text-[12.5px] font-semibold text-ink">Handler profile</p>
              <div className="grid gap-4 sm:grid-cols-3">
                <Field label="Primary expertise" htmlFor="expertise" required error={extraErrors.expertise}>
                  <Select id="expertise" value={expertise} invalid={!!extraErrors.expertise} onChange={(e) => setExpertise(e.target.value as Expertise)}>
                    <option value="">Choose…</option>
                    {options?.expertise.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </Select>
                </Field>
                <Field label="Experience (years)" htmlFor="years" required error={extraErrors.years}>
                  <Input id="years" type="number" min={0} max={60} value={years} invalid={!!extraErrors.years} onChange={(e) => setYears(e.target.value)} />
                </Field>
                <Field label="Base city" htmlFor="city" required error={extraErrors.city}>
                  <Select id="city" value={city} invalid={!!extraErrors.city} onChange={(e) => setCity(e.target.value)}>
                    <option value="">Choose…</option>
                    {options?.cities.map((c) => <option key={c} value={c}>{c}</option>)}
                  </Select>
                </Field>
              </div>
              <p className="mt-3 text-[12px] text-ink-subtle">A supervisor sets your claim capacity after you register. You start as available.</p>
            </div>
          )}
          <Field label="Staff registration code" htmlFor="staffCode" required error={extraErrors.staffCode}>
            <Input id="staffCode" type="password" autoComplete="off" value={code} invalid={!!extraErrors.staffCode} onChange={(e) => setCode(e.target.value)} />
          </Field>
          <Button type="submit" size="lg" className="w-full" loading={pending}>Create staff account</Button>
        </form>
      )}
    </AuthLayout>
  );
}
