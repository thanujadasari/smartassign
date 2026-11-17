import { useState } from 'react';
import { Field, Textarea } from '@/components/ui/input';
import { useToast } from '@/components/ui/toast';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { api, toApiError } from '@/lib/api';
import type { Handler } from '@/lib/types';

export type HandlerAction = { kind: 'status'; handler: Handler } | null;

/** Activation / deactivation confirmation - shared by the directory and the detail page. Availability belongs to the handler. */
export function HandlerActionDialogs({ action, onClose, onDone }: { action: HandlerAction; onClose: () => void; onDone: () => void }) {
  const { notify } = useToast();
  const [reason, setReason] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!action) return null;
  const h = action.handler;

  const close = () => { setReason(''); setError(null); onClose(); };
  const run = async () => {
    setPending(true);
    setError(null);
    try {
      await api.handlers.setActive(h.id, !h.active, reason.trim() || undefined);
      notify('success', `${h.fullName} ${h.active ? 'deactivated' : 'activated'}`);
      setReason('');
      onDone();
      onClose();
    } catch (e) {
      setError(toApiError(e).message);
    } finally {
      setPending(false);
    }
  };

  const copy = {
    status: {
      title: h.active ? `Deactivate ${h.fullName}?` : `Activate ${h.fullName}?`,
      description: h.active ? 'Inactive handlers never receive new claims. Existing assignments are not changed.' : 'The handler will be eligible for new claims again, subject to all other rules.',
      confirm: h.active ? 'Deactivate' : 'Activate',
      tone: h.active ? ('danger' as const) : ('success' as const),
    },
  }[action.kind];

  return (
    <ConfirmDialog open onClose={close} onConfirm={run} pending={pending} error={error} title={copy.title}
      description={copy.description} confirmLabel={copy.confirm} tone={copy.tone}>
      {(
        <Field label="Reason (optional)" htmlFor="handler-reason" hint="Recorded in the audit log, e.g. annual leave">
          <Textarea id="handler-reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} className="min-h-16" />
        </Field>
      )}
    </ConfirmDialog>
  );
}
