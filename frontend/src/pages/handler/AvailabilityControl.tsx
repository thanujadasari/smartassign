import { useState } from 'react';
import { CircleCheck, CircleSlash } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field, Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/misc';
import { useToast } from '@/components/ui/toast';
import { api, toApiError } from '@/lib/api';
import { fmtDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { Handler, HandlerProfile } from '@/lib/types';

/**
 * The handler's only control over assignments: Available or Unavailable (with a reason).
 * There is no accept / reject - claims are assigned by a supervisor.
 */
export function AvailabilityControl({ handler, onChange }: { handler: Handler; onChange: (p: HandlerProfile) => void }) {
  const { notify } = useToast();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const reasonError = touched && reason.trim().length < 5 ? 'Please give a reason (at least 5 characters)' : undefined;

  const save = async (available: boolean) => {
    setPending(true);
    setError(null);
    try {
      const p = await api.handlerPortal.setAvailability(available, available ? undefined : reason.trim());
      onChange(p);
      setOpen(false);
      notify('success', available ? 'You are available for new claims' : 'You are marked unavailable',
        available ? undefined : 'Supervisors will not assign you new claims until you are available again.');
    } catch (e) {
      setError(toApiError(e).message);
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <div className="flex flex-wrap items-center gap-4">
        <div className="min-w-0 flex-1">
          <p className="text-[12px] font-medium uppercase tracking-wide text-ink-subtle">My availability</p>
          <p className={cn('mt-1 flex items-center gap-2 text-[15px] font-semibold', handler.available ? 'text-success' : 'text-warning')}>
            {handler.available ? <CircleCheck className="size-4" /> : <CircleSlash className="size-4" />}
            {handler.available ? 'Available for new claims' : 'Unavailable'}
          </p>
          {!handler.available && handler.unavailableReason && (
            <p className="mt-1 text-[12.5px] text-ink-muted">Reason: {handler.unavailableReason}</p>
          )}
          {handler.availabilityChangedAt && (
            <p className="mt-0.5 text-[12px] text-ink-subtle">Since {fmtDateTime(handler.availabilityChangedAt)}</p>
          )}
        </div>
        <div className="flex gap-2" role="group" aria-label="Set availability">
          <Button variant={handler.available ? 'primary' : 'secondary'} disabled={handler.available || pending}
            loading={pending && !open} onClick={() => save(true)} aria-pressed={handler.available}>
            Available
          </Button>
          <Button variant={!handler.available ? 'primary' : 'secondary'} disabled={pending}
            onClick={() => { setReason(handler.unavailableReason ?? ''); setTouched(false); setError(null); setOpen(true); }}
            aria-pressed={!handler.available}>
            {handler.available ? 'Unavailable' : 'Change reason'}
          </Button>
        </div>
      </div>
      {!handler.active && (
        <Alert tone="warning" className="mt-3">A supervisor has deactivated your profile, so you will not receive new claims.</Alert>
      )}

      <Dialog open={open} onClose={() => setOpen(false)} title="Mark yourself unavailable"
        description="Claims already assigned to you stay with you. Supervisors will not assign you new claims while you are unavailable."
        footer={<>
          <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
          <Button loading={pending} onClick={() => { setTouched(true); if (reason.trim().length >= 5) save(false); }}>Mark unavailable</Button>
        </>}>
        <div className="space-y-3">
          {error && <Alert tone="danger">{error}</Alert>}
          <Field label="Reason for unavailability" htmlFor="unavailable-reason" required error={reasonError}
            hint="For example: annual leave until Friday, training, sick leave">
            <Textarea id="unavailable-reason" data-autofocus rows={3} maxLength={500} value={reason} invalid={!!reasonError}
              onChange={(e) => setReason(e.target.value)} />
          </Field>
        </div>
      </Dialog>
    </div>
  );
}
