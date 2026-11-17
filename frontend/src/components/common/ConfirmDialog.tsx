import type { ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Alert } from '@/components/ui/misc';

export function ConfirmDialog({ open, onClose, onConfirm, title, description, confirmLabel = 'Confirm', tone = 'primary',
  pending, error, children }: {
  open: boolean; onClose: () => void; onConfirm: () => void; title: string; description?: ReactNode; confirmLabel?: string;
  tone?: 'primary' | 'danger' | 'success'; pending?: boolean; error?: string | null; children?: ReactNode;
}) {
  return (
    <Dialog open={open} onClose={pending ? () => undefined : onClose} title={title} description={description} size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>Cancel</Button>
          <Button variant={tone} onClick={onConfirm} loading={pending} data-autofocus>{confirmLabel}</Button>
        </>
      }>
      {(children || error) && (
        <div className="space-y-3">
          {children}
          {error && <Alert tone="danger">{error}</Alert>}
        </div>
      )}
    </Dialog>
  );
}
