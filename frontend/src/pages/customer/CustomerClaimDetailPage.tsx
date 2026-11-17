import { Link, useLocation, useParams } from 'react-router-dom';
import { ArrowLeft, UserRound } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Alert } from '@/components/ui/misc';
import { ClaimStatusBadge } from '@/components/common/StatusBadges';
import { ErrorState, PageSkeleton } from '@/components/common/States';
import { ClaimFacts, PersonCard, Row, Timeline } from '@/components/claims/ClaimParts';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtDateTime } from '@/lib/format';

const CUSTOMER_STEP = { SUBMITTED: 'Submitted', IN_REVIEW: 'Under review', ASSIGNED: 'Handler assigned', RESOLVED: 'Resolved' };

export default function CustomerClaimDetailPage() {
  const id = Number(useParams().id);
  const justSubmitted = (useLocation().state as { submitted?: boolean } | null)?.submitted;
  const res = useAsync(() => api.customer.get(id), [id]);

  if (res.loading && !res.data) return <PageSkeleton />;
  if (res.error) return <ErrorState error={res.error} onRetry={res.reload} />;
  const { claim: c, timeline } = res.data!;

  return (
    <>
      <Link to="/customer" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-ink-muted hover:text-ink">
        <ArrowLeft className="size-4" /> Back to my claims
      </Link>
      {justSubmitted && (
        <Alert tone="success" className="mb-6" title={`Claim ${c.claimNumber} registered`}>
          Our claims team will review it and assign a handler. You can follow its progress on this page.
        </Alert>
      )}
      <div className="mb-6">
        <div className="flex flex-wrap items-center gap-2.5">
          <h1 className="text-[22px] font-semibold tracking-tight text-ink">Claim {c.claimNumber}</h1>
          <ClaimStatusBadge status={c.status} label={c.statusLabel} />
        </div>
        <p className="mt-1 text-[13.5px] text-ink-muted">{CLAIM_TYPE_LABEL[c.claimType]} claim · {c.location}</p>
      </div>

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="min-w-0 space-y-6 xl:col-span-2">
          <ClaimFacts c={c} />
          {c.status === 'RESOLVED' && c.resolutionNote && (
            <Alert tone="success" title={`Resolved on ${fmtDateTime(c.resolvedAt)}`}>{c.resolutionNote}</Alert>
          )}
        </div>
        <div className="min-w-0 space-y-6">
          <Card>
            <CardHeader title="Your claim handler" icon={<UserRound />} />
            <CardBody>
              {c.handler ? (
                <div className="space-y-3">
                  <PersonCard name={c.handler.fullName} caption="Claim handler" email={c.handler.email} phone={c.handler.phone} />
                  <dl><Row label="Assigned" value={fmtDateTime(c.assignedAt)} /></dl>
                </div>
              ) : (
                <p className="text-[13px] text-ink-muted">
                  A handler has not been assigned yet. Our team reviews every claim before assigning the most suitable handler.
                </p>
              )}
            </CardBody>
          </Card>
          <Timeline events={timeline} title="Progress" labels={CUSTOMER_STEP} />
        </div>
      </div>
    </>
  );
}
