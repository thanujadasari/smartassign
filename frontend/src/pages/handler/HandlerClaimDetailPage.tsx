import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, CircleCheckBig, ShieldCheck, UserRound } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field, Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/misc';
import { useToast } from '@/components/ui/toast';
import { ClaimStatusBadge, PriorityBadge, SeverityBadge } from '@/components/common/StatusBadges';
import { ErrorState, PageSkeleton } from '@/components/common/States';
import { ClaimFacts, PersonCard, Row, Timeline } from '@/components/claims/ClaimParts';
import { api, toApiError } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtDateTime } from '@/lib/format';

export default function HandlerClaimDetailPage() {
  const id = Number(useParams().id);
  const { notify } = useToast();
  const res = useAsync(() => api.handlerPortal.claim(id), [id]);
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [touched, setTouched] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (res.loading && !res.data) return <PageSkeleton />;
  if (res.error) return <ErrorState error={res.error} onRetry={res.reload} />;
  const { claim: c, timeline } = res.data!;
  const noteError = touched && note.trim().length < 10 ? 'Describe how the claim was resolved (at least 10 characters)' : undefined;

  const resolve = async () => {
    setTouched(true);
    if (note.trim().length < 10) return;
    setPending(true);
    setError(null);
    try {
      res.setData(await api.handlerPortal.resolve(c.id, note.trim()));
      setOpen(false);
      notify('success', `Claim ${c.claimNumber} resolved`, 'Your workload has been updated.');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      setError(toApiError(e).message);
    } finally {
      setPending(false);
    }
  };

  return (
    <>
      <Link to={c.status === 'RESOLVED' ? '/handler/resolved' : '/handler'} className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-ink-muted hover:text-ink">
        <ArrowLeft className="size-4" /> Back to my claims
      </Link>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-[22px] font-semibold tracking-tight text-ink">Claim {c.claimNumber}</h1>
            <ClaimStatusBadge status={c.status} />
            <PriorityBadge priority={c.priority.priority} />
            <SeverityBadge severity={c.severity} />
          </div>
          <p className="mt-1 text-[13.5px] text-ink-muted">{CLAIM_TYPE_LABEL[c.claimType]} claim · {c.location}</p>
        </div>
        {c.status === 'ASSIGNED' && (
          <Button onClick={() => { setNote(''); setTouched(false); setError(null); setOpen(true); }}><CircleCheckBig /> Mark resolved</Button>
        )}
      </div>

      {c.status === 'RESOLVED' && (
        <Alert tone="success" className="mb-6" title={`Resolved ${fmtDateTime(c.resolvedAt)} · ${c.resolutionDays} days after assignment`}>
          {c.resolutionNote}
        </Alert>
      )}

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="min-w-0 space-y-6 xl:col-span-2">
          <ClaimFacts c={c} triage={c} />
          {c.triage?.note && (
            <Card><CardHeader title="Supervisor's triage note" /><CardBody><p className="text-[13px] text-ink">{c.triage.note}</p></CardBody></Card>
          )}
        </div>
        <div className="min-w-0 space-y-6">
          <Card>
            <CardHeader title="Assigned by" icon={<ShieldCheck />} />
            <CardBody className="space-y-3">
              {c.assignedBy ? <PersonCard name={c.assignedBy.fullName} caption="Supervisor" email={c.assignedBy.email} phone={c.assignedBy.phone} />
                : <p className="text-[13px] text-ink-muted">Not recorded.</p>}
              <dl><Row label="Assigned" value={fmtDateTime(c.assignedAt)} /></dl>
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Customer" icon={<UserRound />} />
            <CardBody>
              <PersonCard name={c.customer.fullName} caption="Claimant" email={c.customer.email} phone={c.contactPhone ?? c.customer.phone} />
            </CardBody>
          </Card>
          <Timeline events={timeline} />
        </div>
      </div>

      <Dialog open={open} onClose={() => setOpen(false)} title={`Resolve claim ${c.claimNumber}`}
        description="The claim will be marked resolved, the customer will see your note, and your workload goes down by one."
        footer={<>
          <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
          <Button loading={pending} onClick={resolve}>Mark resolved</Button>
        </>}>
        <div className="space-y-3">
          {error && <Alert tone="danger">{error}</Alert>}
          <Field label="Resolution note" htmlFor="resolution-note" required error={noteError}>
            <Textarea id="resolution-note" data-autofocus rows={4} maxLength={2000} value={note} invalid={!!noteError} onChange={(e) => setNote(e.target.value)} />
          </Field>
        </div>
      </Dialog>
    </>
  );
}
