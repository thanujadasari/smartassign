import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field, Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/misc';
import { ScoreRing } from '@/components/common/Score';
import type { Recommendation } from '@/lib/types';

export type Decision =
  | { kind: 'approve'; rec: Recommendation }
  | { kind: 'override'; rec: Recommendation; top: Recommendation }
  | { kind: 'review' };

export function DecisionDialog({ decision, onClose, onConfirm, pending, error, claimNumber }: {
  decision: Decision | null; onClose: () => void; onConfirm: (text: string) => void; pending: boolean; error: string | null;
  claimNumber: string;
}) {
  const [text, setText] = useState('');
  const [touched, setTouched] = useState(false);
  useEffect(() => { setText(''); setTouched(false); }, [decision]);
  if (!decision) return null;

  const min = decision.kind === 'override' ? 10 : decision.kind === 'review' ? 5 : 0;
  const invalid = text.trim().length < min;
  const submit = () => { setTouched(true); if (!invalid) onConfirm(text.trim()); };

  const title = decision.kind === 'approve' ? 'Approve recommendation' : decision.kind === 'override' ? 'Override recommendation' : 'Send for manual review';
  const description = decision.kind === 'approve'
    ? `Assign claim ${claimNumber} to the recommended handler.`
    : decision.kind === 'override'
      ? `Assign claim ${claimNumber} to a different eligible handler. A reason is required and recorded in the audit log.`
      : `Place claim ${claimNumber} in the manual review queue without assigning a handler.`;

  return (
    <Dialog open onClose={pending ? () => undefined : onClose} title={title} description={description} size="md"
      footer={<>
        <Button variant="secondary" onClick={onClose} disabled={pending}>Cancel</Button>
        <Button variant={decision.kind === 'review' ? 'warning' : 'primary'} onClick={submit} loading={pending}>
          {decision.kind === 'approve' ? 'Confirm assignment' : decision.kind === 'override' ? 'Confirm override' : 'Send for review'}
        </Button>
      </>}>
      <div className="space-y-4">
        {decision.kind !== 'review' && (
          <div className="flex items-center gap-4 rounded-lg border border-border p-3.5">
            <ScoreRing score={decision.rec.suitabilityScore} size={48} />
            <div className="min-w-0">
              <p className="text-[14px] font-semibold text-ink">{decision.rec.handler.fullName} <span className="font-normal text-ink-subtle">{decision.rec.handler.code}</span></p>
              <p className="text-[12.5px] text-ink-muted">Rank #{decision.rec.rank} · {decision.rec.handler.location} · workload {decision.rec.handler.currentWorkload}/{decision.rec.handler.maxWorkload} → {decision.rec.handler.currentWorkload + 1}/{decision.rec.handler.maxWorkload}</p>
            </div>
          </div>
        )}
        {decision.kind === 'override' && (
          <Alert tone="info">
            The recommended handler was <strong>{decision.top.handler.fullName}</strong> (suitability {decision.top.suitabilityScore.toFixed(1)}).
            The selected handler scores {decision.rec.suitabilityScore.toFixed(1)}. Eligibility is re-checked before the assignment is saved.
          </Alert>
        )}
        {decision.kind === 'approve' && (
          <p className="text-[12.5px] text-ink-muted">Eligibility is re-checked against live data before saving. The handler’s workload, the claim status and the audit log are updated together.</p>
        )}
        <Field htmlFor="decision-text" required={min > 0}
          label={decision.kind === 'approve' ? 'Note (optional)' : decision.kind === 'override' ? 'Override reason' : 'Reason for manual review'}
          error={touched && invalid ? `Please enter at least ${min} characters` : undefined}
          hint={decision.kind === 'override' ? 'e.g. Handler already manages the related policy file' : undefined}>
          <Textarea id="decision-text" value={text} onChange={(e) => setText(e.target.value)} maxLength={1000}
            invalid={touched && invalid} data-autofocus />
        </Field>
        {error && <Alert tone="danger">{error}</Alert>}
      </div>
    </Dialog>
  );
}
