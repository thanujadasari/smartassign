import { useState } from 'react';
import { Flag } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field, Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/misc';
import { PriorityBadge } from '@/components/common/StatusBadges';
import { useToast } from '@/components/ui/toast';
import { api, toApiError } from '@/lib/api';
import { fmtDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { Claim, ClaimDetail, Priority } from '@/lib/types';
import { PRIORITY_HELP } from '../triage/TriagePanel';

const PRIORITIES: Priority[] = ['HIGH', 'MEDIUM', 'LOW'];

/** Shows the decided priority (and the rule suggestion) and lets the supervisor change it until the claim is resolved. */
export function PriorityCard({ claim, onChanged }: { claim: Claim; onChanged: (d: ClaimDetail) => void }) {
  const { notify } = useToast();
  const p = claim.priority;
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState<Priority>(p.priority ?? 'MEDIUM');
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const canChange = claim.status !== 'SUBMITTED' && claim.status !== 'RESOLVED';
  const reasonError = touched && reason.trim().length < 5 ? 'Give a reason (at least 5 characters)' : undefined;

  const save = async () => {
    setTouched(true);
    if (reason.trim().length < 5 || value === p.priority) return;
    setPending(true);
    setError(null);
    try {
      onChanged(await api.claims.changePriority(claim.id, value, reason.trim()));
      notify('success', `Priority set to ${value.toLowerCase()}`);
      setOpen(false);
    } catch (e) {
      setError(toApiError(e).message);
    } finally {
      setPending(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Priority" icon={<Flag />}
        action={canChange ? <Button size="sm" variant="secondary" onClick={() => {
          setValue(p.priority ?? 'MEDIUM'); setReason(''); setTouched(false); setError(null); setOpen(true);
        }}>Change</Button> : undefined} />
      <CardBody className="space-y-3">
        {p.priority ? <PriorityBadge priority={p.priority} /> : <PriorityBadge priority={p.suggestedPriority} suggested />}
        {p.suggestedPriority && (
          <div className="text-[12.5px] text-ink-muted">
            <p>Rules suggested <span className="font-medium text-ink">{p.suggestedPriority.toLowerCase()}</span>:</p>
            <ul className="mt-1 list-disc space-y-0.5 pl-5">{p.suggestedReasons.map((r, i) => <li key={i}>{r.text}</li>)}</ul>
          </div>
        )}
        {p.note && <p className="rounded-md bg-canvas p-2.5 text-[12.5px] text-ink">{p.note}</p>}
        {p.setAt && <p className="text-[12px] text-ink-subtle">Set {p.setBy ? `by ${p.setBy.fullName} ` : ''}{fmtDateTime(p.setAt)}</p>}
        {claim.status === 'SUBMITTED' && <p className="text-[12.5px] text-ink-muted">Confirm the priority while triaging this claim.</p>}
      </CardBody>

      <Dialog open={open} onClose={() => setOpen(false)} title={`Change priority of ${claim.claimNumber}`}
        description="The new priority applies straight away: queues are ordered most urgent first."
        footer={<>
          <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
          <Button loading={pending} disabled={value === p.priority} onClick={save}>Save priority</Button>
        </>}>
        <div className="space-y-4">
          {error && <Alert tone="danger">{error}</Alert>}
          <div className="grid gap-2" role="radiogroup" aria-label="Priority">
            {PRIORITIES.map((v) => (
              <button key={v} type="button" role="radio" aria-checked={value === v} onClick={() => setValue(v)}
                className={cn('cursor-pointer rounded-md border px-3 py-2.5 text-left',
                  value === v ? 'border-navy-900 bg-navy-50' : 'border-border hover:border-border-strong')}>
                <span className="flex items-center gap-2">
                  <PriorityBadge priority={v} />
                  {v === p.priority && <span className="text-[11px] text-ink-subtle">current</span>}
                </span>
                <span className="mt-1 block text-[11.5px] text-ink-muted">{PRIORITY_HELP[v]}</span>
              </button>
            ))}
          </div>
          <Field label="Reason for the change" htmlFor="priority-reason" required error={reasonError}>
            <Textarea id="priority-reason" rows={2} maxLength={500} value={reason} invalid={!!reasonError} onChange={(e) => setReason(e.target.value)} />
          </Field>
        </div>
      </Dialog>
    </Card>
  );
}
