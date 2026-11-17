import { History, UserRound } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { PageHeader } from '@/components/common/PageHeader';
import { EmptyState, ErrorState, PageSkeleton } from '@/components/common/States';
import { Row } from '@/components/claims/ClaimParts';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtDateTime, fmtPercent } from '@/lib/format';
import { AvailabilityControl } from './AvailabilityControl';

export default function HandlerProfilePage() {
  const res = useAsync(() => api.handlerPortal.me(), []);
  if (res.loading && !res.data) return <PageSkeleton />;
  if (res.error) return <ErrorState error={res.error} onRetry={res.reload} />;
  const { handler: h, availabilityHistory, resolutionTargetDays } = res.data!;

  return (
    <>
      <PageHeader title="My profile & availability" description="Expertise, capacity and location are maintained by your supervisor." />
      <div className="grid gap-6 xl:grid-cols-3">
        <div className="min-w-0 space-y-6 xl:col-span-2">
          <AvailabilityControl handler={h} onChange={(p) => res.setData(p)} />
          <Card>
            <CardHeader title="Availability history" icon={<History />} />
            {availabilityHistory.length === 0 ? <EmptyState title="No changes yet" description="You have been available since you registered." /> : (
              <ul className="divide-y divide-border">
                {availabilityHistory.map((e, i) => (
                  <li key={i} className="flex flex-wrap items-start justify-between gap-2 px-5 py-3">
                    <div>
                      <Badge tone={e.available ? 'success' : 'warning'} dot>{e.available ? 'Available' : 'Unavailable'}</Badge>
                      {e.reason && <p className="mt-1 text-[12.5px] text-ink-muted">{e.reason}</p>}
                    </div>
                    <span className="text-[12px] text-ink-subtle">{fmtDateTime(e.changedAt)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
        <Card className="h-fit">
          <CardHeader title={h.fullName} description={`Handler ${h.code}`} icon={<UserRound />} />
          <CardBody><dl className="space-y-3">
            <Row label="Email" value={h.email ?? '—'} />
            <Row label="Phone" value={h.phone ?? '—'} />
            <Row label="Primary expertise" value={h.primaryExpertiseLabel} />
            <Row label="Skills" value={h.skills.map((s) => CLAIM_TYPE_LABEL[s]).join(', ')} />
            <Row label="Experience" value={`${h.experienceYears} years`} />
            <Row label="Base city" value={h.location} />
            <Row label="Capacity" value={`${h.currentWorkload} of ${h.maxWorkload} open claims`} />
            <Row label="Resolved claims" value={h.resolvedCount} />
            <Row label="Resolved on time" value={h.hasHistory ? `${fmtPercent(h.successRate)} (target ${resolutionTargetDays} days)` : 'No history yet'} />
            <Row label="Average resolution" value={h.hasHistory ? `${h.avgResolutionDays} days` : 'No history yet'} />
          </dl></CardBody>
        </Card>
      </div>
    </>
  );
}
