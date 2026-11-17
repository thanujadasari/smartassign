import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Checkbox, Field, Input, Select } from '@/components/ui/input';
import { Alert } from '@/components/ui/misc';
import { useToast } from '@/components/ui/toast';
import { api, toApiError, type ApiError } from '@/lib/api';
import { useOptions } from '@/lib/options';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import type { ClaimType, Expertise, Handler } from '@/lib/types';

const CLAIM_TYPES: ClaimType[] = ['AUTO', 'PROPERTY', 'LIABILITY', 'WORKERS_COMPENSATION'];

/**
 * What a supervisor maintains: expertise, extra skills, experience, capacity and base city.
 * Availability is the handler's own choice and statistics come from resolved claims, so neither is editable here.
 */
export function HandlerEditDialog({ open, handler, onClose, onSaved }:
  { open: boolean; handler: Handler | null; onClose: () => void; onSaved: (h: Handler) => void }) {
  const options = useOptions();
  const { notify } = useToast();
  const [expertise, setExpertise] = useState<Expertise>('AUTO');
  const [extra, setExtra] = useState<ClaimType[]>([]);
  const [years, setYears] = useState('0');
  const [capacity, setCapacity] = useState('10');
  const [city, setCity] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    if (!open || !handler) return;
    setExpertise(handler.primaryExpertise);
    setExtra(handler.skills.filter((s) => s !== handler.primaryExpertise));
    setYears(String(handler.experienceYears));
    setCapacity(String(handler.maxWorkload));
    setCity(handler.location);
    setError(null);
  }, [open, handler]);

  if (!handler) return null;
  const y = Number(years);
  const cap = Number(capacity);
  const yearsErr = !Number.isInteger(y) || y < 0 || y > 60 ? 'Whole years, 0 to 60' : undefined;
  const capErr = !Number.isInteger(cap) || cap < 1 || cap > 200 ? 'Between 1 and 200'
    : cap < handler.currentWorkload ? `Below the ${handler.currentWorkload} claims currently assigned` : undefined;

  const save = async () => {
    if (yearsErr || capErr || !city) return;
    setPending(true);
    setError(null);
    try {
      const h = await api.handlers.update(handler.id, {
        primaryExpertise: expertise, additionalSkills: expertise === 'MULTI_P_AND_C' ? [] : extra.filter((s) => s !== expertise),
        experienceYears: y, maxWorkload: cap, location: city,
      });
      notify('success', `${h.fullName} updated`, 'The change applies to the next routing decision.');
      onSaved(h);
      onClose();
    } catch (e) {
      setError(toApiError(e));
    } finally {
      setPending(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} title={`Edit ${handler.fullName}`} description={`Handler ${handler.code} · ${handler.email ?? ''}`} size="lg"
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={pending} onClick={save}>Save changes</Button></>}>
      <div className="space-y-4">
        {error && <Alert tone="danger">{error.message}</Alert>}
        {capErr && cap < handler.currentWorkload && <Alert tone="warning">Capacity below the current workload would block every new assignment to this handler.</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Primary expertise" htmlFor="he-exp" required>
            <Select id="he-exp" value={expertise} onChange={(e) => setExpertise(e.target.value as Expertise)}>
              {options?.expertise.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </Select>
          </Field>
          <Field label="Base city" htmlFor="he-city" required>
            <Select id="he-city" value={city} onChange={(e) => setCity(e.target.value)}>
              {options?.cities.map((c) => <option key={c} value={c}>{c}</option>)}
            </Select>
          </Field>
          <Field label="Experience (years)" htmlFor="he-years" required error={yearsErr}>
            <Input id="he-years" type="number" min={0} max={60} value={years} invalid={!!yearsErr} onChange={(e) => setYears(e.target.value)} />
          </Field>
          <Field label="Maximum open claims (capacity)" htmlFor="he-cap" required error={capErr} hint={`Currently ${handler.currentWorkload} open`}>
            <Input id="he-cap" type="number" min={1} max={200} value={capacity} invalid={!!capErr} onChange={(e) => setCapacity(e.target.value)} />
          </Field>
        </div>
        {expertise !== 'MULTI_P_AND_C' && (
          <fieldset>
            <legend className="mb-2 text-[12.5px] font-medium text-ink">Additional skills</legend>
            <div className="flex flex-wrap gap-4">
              {CLAIM_TYPES.filter((t) => t !== expertise).map((t) => (
                <Checkbox key={t} id={`he-skill-${t}`} label={CLAIM_TYPE_LABEL[t]} checked={extra.includes(t)}
                  onChange={(v) => setExtra((p) => (v ? [...p, t] : p.filter((x) => x !== t)))} />
              ))}
            </div>
          </fieldset>
        )}
        <p className="text-[12px] text-ink-subtle">Availability is set by the handler. Success rate and resolution time are calculated from the claims they resolve.</p>
      </div>
    </Dialog>
  );
}
