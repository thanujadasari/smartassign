import { useEffect, useState } from 'react';
import { ClipboardCheck, Info, Lightbulb } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Field, Input, Select, Textarea } from '@/components/ui/input';
import { Alert, Skeleton } from '@/components/ui/misc';
import { ErrorState } from '@/components/common/States';
import { PriorityBadge } from '@/components/common/StatusBadges';
import { cn } from '@/lib/utils';
import { api, toApiError, type ApiError } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { fmtPercent, titleCase } from '@/lib/format';
import type { Claim, ClaimDetail, Complexity, Priority, Severity, TriageReason } from '@/lib/types';

const PRIORITIES: Priority[] = ['HIGH', 'MEDIUM', 'LOW'];
const SEVERITIES: Severity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export const PRIORITY_HELP: Record<Priority, string> = {
  HIGH: 'Serious injury accidents, fire or major property damage, ongoing risk, needs immediate attention',
  MEDIUM: 'Moderate vehicle or property damage, or needs investigation without immediate danger',
  LOW: 'Minor scratches or small damage, no immediate intervention needed',
};
const COMPLEXITIES: Complexity[] = ['LOW', 'MEDIUM', 'HIGH'];

function Basis({ basis }: { basis: TriageReason['basis'] }) {
  return basis === 'DATA'
    ? <Badge tone="info">From historical claims</Badge>
    : <Badge tone="outline">Assumed business rule</Badge>;
}

/**
 * The rules RECOMMEND severity, complexity and fraud risk; the supervisor decides. A decision that differs from the
 * recommendation needs a note, and both the recommendation and the decision are stored.
 */
export function TriagePanel({ claim, onDone, onCancel }: { claim: Claim; onDone: (d: ClaimDetail) => void; onCancel?: () => void }) {
  const sug = useAsync(() => api.claims.triageSuggestion(claim.id), [claim.id]);
  const [priority, setPriority] = useState<Priority | ''>(claim.priority.priority ?? '');
  const [severity, setSeverity] = useState<Severity | ''>(claim.severity ?? '');
  const [complexity, setComplexity] = useState<Complexity | ''>(claim.complexity ?? '');
  const [fraud, setFraud] = useState(claim.fraudRiskScore == null ? '' : String(Math.round(claim.fraudRiskScore * 100)));
  const [note, setNote] = useState(claim.triage?.note ?? '');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  // pre-fill with the suggestion for a claim that has never been triaged
  useEffect(() => {
    if (!sug.data || claim.severity) return;
    setPriority(sug.data.priority);
    setSeverity(sug.data.severity);
    setComplexity(sug.data.complexity);
    setFraud(String(Math.round(sug.data.fraudRiskScore * 100)));
  }, [sug.data, claim.severity]);

  if (sug.error) return <ErrorState error={sug.error} onRetry={sug.reload} compact />;
  if (!sug.data) return <Skeleton className="h-80" />;
  const s = sug.data;

  const fraudNum = Number(fraud);
  const fraudValid = fraud !== '' && Number.isFinite(fraudNum) && fraudNum >= 0 && fraudNum <= 100;
  const differs = priority !== s.priority || severity !== s.severity || complexity !== s.complexity || (fraudValid && Math.abs(fraudNum / 100 - s.fraudRiskScore) > 0.0005);
  const needsNote = differs && note.trim().length < 10;
  const canSave = !!priority && !!severity && !!complexity && fraudValid && !needsNote;

  const accept = () => {
    setPriority(s.priority);
    setSeverity(s.severity);
    setComplexity(s.complexity);
    setFraud(String(Math.round(s.fraudRiskScore * 100)));
  };

  const save = async () => {
    if (!canSave) return;
    setPending(true);
    setError(null);
    try {
      onDone(await api.claims.triage(claim.id, {
        priority: priority as Priority, severity: severity as Severity, complexity: complexity as Complexity,
        fraudRiskScore: Math.round(fraudNum) / 100, note: note.trim() || undefined,
      }));
    } catch (e) {
      setError(toApiError(e));
    } finally {
      setPending(false);
    }
  };

  return (
    <Card className="border-navy-300">
      <CardHeader title={claim.status === 'SUBMITTED' ? 'Triage this claim' : 'Re-triage this claim'} icon={<ClipboardCheck />}
        description="The rules below recommend values. You make the final decision before the claim can be routed." />
      <CardBody className="space-y-5">
        {error && <Alert tone="danger">{error.message}</Alert>}

        <div className="rounded-lg border border-border bg-canvas p-4">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <p className="flex items-center gap-2 text-[13px] font-semibold text-ink"><Lightbulb className="size-4 text-accent-600" /> Recommendation</p>
            <Button size="sm" variant="secondary" onClick={accept}>Use recommendation</Button>
          </div>
          <div className="mb-4 rounded-md border border-border bg-surface p-3">
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-[12px] text-ink-muted">Priority</p>
              <PriorityBadge priority={s.priority} />
            </div>
            <ul className="mt-1.5 space-y-1">{s.priorityReasons.map((r, i) => (
              <li key={i} className="text-[12px] text-ink-muted">{r.text}</li>
            ))}</ul>
            <div className="mt-1.5"><Basis basis="ASSUMED" /></div>
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            <div>
              <p className="text-[12px] text-ink-muted">Severity</p>
              <p className="text-[16px] font-semibold text-ink">{titleCase(s.severity)}</p>
              <ul className="mt-1.5 space-y-1.5">{s.severityReasons.map((r, i) => (
                <li key={i} className="text-[12px] text-ink-muted">{r.text}<div className="mt-0.5"><Basis basis={r.basis} /></div></li>
              ))}</ul>
            </div>
            <div>
              <p className="text-[12px] text-ink-muted">Complexity</p>
              <p className="text-[16px] font-semibold text-ink">{titleCase(s.complexity)}</p>
              <ul className="mt-1.5 space-y-1.5">{s.complexityReasons.map((r, i) => (
                <li key={i} className="text-[12px] text-ink-muted">{r.text}<div className="mt-0.5"><Basis basis={r.basis} /></div></li>
              ))}</ul>
            </div>
            <div>
              <p className="text-[12px] text-ink-muted">Fraud risk</p>
              <p className="text-[16px] font-semibold text-ink">{fmtPercent(s.fraudRiskScore)}</p>
              {s.fraudFlags.length === 0 ? <p className="mt-1.5 text-[12px] text-ink-muted">No red flags found.</p> : (
                <ul className="mt-1.5 space-y-1">{s.fraudFlags.map((f) => (
                  <li key={f.code} className="text-[12px] text-ink-muted">+{Math.round(f.weight * 100)} · {f.text}</li>
                ))}</ul>
              )}
              <div className="mt-1.5"><Basis basis="ASSUMED" /></div>
            </div>
          </div>
          <p className="mt-3 flex gap-2 text-[11.5px] text-ink-subtle"><Info className="mt-px size-3.5 shrink-0" /> {s.fraudNote}</p>
        </div>

        <fieldset>
          <legend className="mb-1.5 text-[12.5px] font-medium text-ink">Priority <span className="text-danger">*</span></legend>
          <div className="grid gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Priority">
            {PRIORITIES.map((v) => (
              <button key={v} type="button" role="radio" aria-checked={priority === v} onClick={() => setPriority(v)}
                className={cn('cursor-pointer rounded-md border px-3 py-2.5 text-left transition-colors',
                  priority === v ? 'border-navy-900 bg-navy-50' : 'border-border hover:border-border-strong')}>
                <span className="flex items-center gap-2">
                  <PriorityBadge priority={v} />
                  {v === s.priority && <span className="text-[11px] text-ink-subtle">recommended</span>}
                </span>
                <span className="mt-1 block text-[11.5px] leading-snug text-ink-muted">{PRIORITY_HELP[v]}</span>
              </button>
            ))}
          </div>
        </fieldset>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Severity" htmlFor="tri-sev" required>
            <Select id="tri-sev" value={severity} onChange={(e) => setSeverity(e.target.value as Severity)}>
              <option value="">Choose…</option>
              {SEVERITIES.map((v) => <option key={v} value={v}>{titleCase(v)}</option>)}
            </Select>
          </Field>
          <Field label="Complexity" htmlFor="tri-cx" required>
            <Select id="tri-cx" value={complexity} onChange={(e) => setComplexity(e.target.value as Complexity)}>
              <option value="">Choose…</option>
              {COMPLEXITIES.map((v) => <option key={v} value={v}>{titleCase(v)}</option>)}
            </Select>
          </Field>
          <Field label="Fraud risk (%)" htmlFor="tri-fr" required error={fraud !== '' && !fraudValid ? 'Enter 0 to 100' : undefined}>
            <Input id="tri-fr" type="number" min={0} max={100} step={1} value={fraud} invalid={fraud !== '' && !fraudValid} onChange={(e) => setFraud(e.target.value)} />
          </Field>
        </div>
        <Field label={differs ? 'Reason for your decision' : 'Note (optional)'} htmlFor="tri-note" required={differs}
          error={needsNote && note.length > 0 ? 'At least 10 characters' : undefined}
          hint={differs ? 'Your decision differs from the recommendation - explain why (at least 10 characters). It is kept in the audit log.' : 'Visible to supervisors and the assigned handler.'}>
          <Textarea id="tri-note" rows={2} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        <div className="flex flex-wrap justify-end gap-2">
          {onCancel && <Button variant="secondary" onClick={onCancel}>Cancel</Button>}
          <Button onClick={save} loading={pending} disabled={!canSave}>Confirm triage</Button>
        </div>
      </CardBody>
    </Card>
  );
}
