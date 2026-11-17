import { useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Card, CardBody } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Checkbox, Field, Input, Select, Textarea } from '@/components/ui/input';
import { Alert, Skeleton } from '@/components/ui/misc';
import { PageHeader } from '@/components/common/PageHeader';
import { api, toApiError, type ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useOptions } from '@/lib/options';
import { titleCase } from '@/lib/format';
import type { ClaimType } from '@/lib/types';

interface Form {
  claimType: ClaimType | ''; policyType: string; policyNumber: string; incidentType: string; incidentDate: string;
  location: string; estimatedLoss: string; injuryInvolved: boolean; thirdPartyInvolved: boolean; ongoingRisk: boolean; description: string;
  contactPhone: string;
}

const today = () => new Date().toISOString().slice(0, 10);

/** Policy numbers are P + digits (e.g. P0001). The P is fixed in the field; a typed leading p/P is ignored. */
export const policyDigits = (v: string) => v.trim().replace(/^[pP]/, '');

export function validateClaim(f: Form): Partial<Record<keyof Form, string>> {
  const e: Partial<Record<keyof Form, string>> = {};
  if (!f.claimType) e.claimType = 'Choose the type of claim';
  if (!f.policyType) e.policyType = 'Choose the policy type';
  if (!/^[0-9]{4,12}$/.test(policyDigits(f.policyNumber))) e.policyNumber = 'Enter the digits after P (4 to 12 digits), e.g. 0001';
  if (!f.incidentType) e.incidentType = 'Choose what happened';
  if (!f.incidentDate) e.incidentDate = 'Enter the date of the incident';
  else if (f.incidentDate > today()) e.incidentDate = 'The date cannot be in the future';
  if (!f.location) e.location = 'Choose where it happened';
  const loss = Number(f.estimatedLoss);
  if (!f.estimatedLoss || !Number.isFinite(loss) || loss <= 0) e.estimatedLoss = 'Enter your estimate of the loss in rupees';
  if (f.description.trim().length < 20) e.description = 'Please describe what happened (at least 20 characters)';
  if (f.contactPhone.trim() && !/^[0-9+()\- ]{7,30}$/.test(f.contactPhone.trim())) e.contactPhone = 'Enter a valid phone number';
  return e;
}

export default function ReportClaimPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const options = useOptions();
  const [f, setF] = useState<Form>({
    claimType: '', policyType: '', policyNumber: '', incidentType: '', incidentDate: '', location: '', estimatedLoss: '',
    injuryInvolved: false, thirdPartyInvolved: false, ongoingRisk: false, description: '', contactPhone: user?.phone ?? '',
  });
  const [touched, setTouched] = useState(false);
  const [pending, setPending] = useState(false);
  const [server, setServer] = useState<ApiError | null>(null);
  const errors = touched ? validateClaim(f) : {};
  const err = (k: keyof Form) => errors[k] ?? server?.fieldErrors[k];
  const set = <K extends keyof Form>(k: K, v: Form[K]) => setF((p) => ({ ...p, [k]: v }));

  const policyTypes = useMemo(() => (f.claimType && options ? options.policyTypesByClaimType[f.claimType] ?? [] : []), [f.claimType, options]);
  const incidentTypes = useMemo(() => (f.claimType && options ? options.incidentTypesByClaimType[f.claimType] ?? [] : []), [f.claimType, options]);

  const chooseType = (t: ClaimType | '') => {
    const p = t && options ? options.policyTypesByClaimType[t] ?? [] : [];
    setF((prev) => ({ ...prev, claimType: t, policyType: p.length === 1 ? p[0] : '', incidentType: '' }));
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(validateClaim(f)).length) return;
    setPending(true);
    setServer(null);
    try {
      const res = await api.customer.submit({
        claimType: f.claimType as ClaimType, policyType: f.policyType, policyNumber: 'P' + policyDigits(f.policyNumber),
        incidentType: f.incidentType, incidentDate: f.incidentDate, location: f.location,
        estimatedLoss: Number(f.estimatedLoss), injuryInvolved: f.injuryInvolved, thirdPartyInvolved: f.thirdPartyInvolved, ongoingRisk: f.ongoingRisk,
        description: f.description.trim(), contactPhone: f.contactPhone.trim() || undefined,
      });
      navigate(`/customer/claims/${res.claim.id}`, { replace: true, state: { submitted: true } });
    } catch (ex) {
      setServer(toApiError(ex));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } finally {
      setPending(false);
    }
  };

  return (
    <>
      <Link to="/customer" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-ink-muted hover:text-ink">
        <ArrowLeft className="size-4" /> Back to my claims
      </Link>
      <PageHeader title="Report a claim" description="Tell us what happened. Our claims team reviews every claim and assigns a handler." />
      {!options ? <Skeleton className="h-96" /> : (
        <form onSubmit={submit} noValidate className="max-w-3xl space-y-6">
          {server && <Alert tone="danger">{server.message}</Alert>}
          <Card><CardBody className="space-y-4">
            <h2 className="text-[14px] font-semibold text-ink">Policy</h2>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="Type of claim" htmlFor="claimType" required error={err('claimType')}>
                <Select id="claimType" value={f.claimType} invalid={!!err('claimType')} onChange={(e) => chooseType(e.target.value as ClaimType | '')}>
                  <option value="">Choose…</option>
                  {options.claimTypes.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </Select>
              </Field>
              <Field label="Policy type" htmlFor="policyType" required error={err('policyType')}>
                <Select id="policyType" value={f.policyType} disabled={!f.claimType} invalid={!!err('policyType')} onChange={(e) => set('policyType', e.target.value)}>
                  <option value="">{f.claimType ? 'Choose…' : 'Choose the claim type first'}</option>
                  {policyTypes.map((p) => <option key={p} value={p}>{titleCase(p)}</option>)}
                </Select>
              </Field>
              <Field label="Policy number" htmlFor="policyNumber" required error={err('policyNumber')} hint="Format P0001">
                <div className="relative">
                  <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[13.5px] font-medium text-ink-muted" aria-hidden>P</span>
                  <Input id="policyNumber" inputMode="numeric" placeholder="0001" className="pl-7" maxLength={13} value={f.policyNumber}
                    invalid={!!err('policyNumber')} aria-describedby="policyNumber-prefix"
                    onChange={(e) => set('policyNumber', e.target.value.replace(/^[pP]/, '').replace(/[^0-9]/g, ''))} />
                  <span id="policyNumber-prefix" className="sr-only">Starts with P</span>
                </div>
              </Field>
            </div>
          </CardBody></Card>

          <Card><CardBody className="space-y-4">
            <h2 className="text-[14px] font-semibold text-ink">Incident</h2>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="What happened" htmlFor="incidentType" required error={err('incidentType')}>
                <Select id="incidentType" value={f.incidentType} disabled={!f.claimType} invalid={!!err('incidentType')} onChange={(e) => set('incidentType', e.target.value)}>
                  <option value="">{f.claimType ? 'Choose…' : 'Choose the claim type first'}</option>
                  {incidentTypes.map((p) => <option key={p} value={p}>{titleCase(p)}</option>)}
                </Select>
              </Field>
              <Field label="Date of incident" htmlFor="incidentDate" required error={err('incidentDate')}>
                <Input id="incidentDate" type="date" max={today()} value={f.incidentDate} invalid={!!err('incidentDate')} onChange={(e) => set('incidentDate', e.target.value)} />
              </Field>
              <Field label="City" htmlFor="location" required error={err('location')}>
                <Select id="location" value={f.location} invalid={!!err('location')} onChange={(e) => set('location', e.target.value)}>
                  <option value="">Choose…</option>
                  {options.cities.map((c) => <option key={c} value={c}>{c}</option>)}
                </Select>
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="Estimated loss (₹)" htmlFor="estimatedLoss" required error={err('estimatedLoss')} hint="Your best estimate">
                <Input id="estimatedLoss" type="number" min={1} step="1" inputMode="numeric" value={f.estimatedLoss} invalid={!!err('estimatedLoss')}
                  onChange={(e) => set('estimatedLoss', e.target.value)} />
              </Field>
              <div className="flex flex-col justify-center gap-3 sm:col-span-2 sm:pt-5">
                <Checkbox id="injury" checked={f.injuryInvolved} onChange={(v) => set('injuryInvolved', v)} label="Someone was injured" />
                <Checkbox id="thirdParty" checked={f.thirdPartyInvolved} onChange={(v) => set('thirdPartyInvolved', v)} label="Another person or vehicle was involved" />
                <Checkbox id="ongoingRisk" checked={f.ongoingRisk} onChange={(v) => set('ongoingRisk', v)}
                  label="The danger or damage is still ongoing (e.g. water still leaking, unsafe structure, risk of fire)" />
              </div>
            </div>
            <Field label="Describe what happened" htmlFor="description" required error={err('description')} hint={`${f.description.trim().length} / 4000`}>
              <Textarea id="description" rows={5} maxLength={4000} value={f.description} invalid={!!err('description')} onChange={(e) => set('description', e.target.value)} />
            </Field>
          </CardBody></Card>

          <Card><CardBody className="space-y-4">
            <h2 className="text-[14px] font-semibold text-ink">Contact</h2>
            <Field label="Phone number for this claim" htmlFor="contactPhone" error={err('contactPhone')} hint="Optional - the handler may call you on this number">
              <Input id="contactPhone" type="tel" className="sm:max-w-xs" value={f.contactPhone} invalid={!!err('contactPhone')} onChange={(e) => set('contactPhone', e.target.value)} />
            </Field>
          </CardBody></Card>

          <div className="flex flex-wrap justify-end gap-2">
            <Link to="/customer" className="inline-flex h-9 items-center rounded-md px-4 text-[13.5px] font-medium text-ink-muted hover:bg-navy-50">Cancel</Link>
            <Button type="submit" loading={pending}>Submit claim</Button>
          </div>
        </form>
      )}
    </>
  );
}
