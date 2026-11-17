import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CircleCheckBig, ClipboardCheck, Gauge, ListChecks, Timer, Briefcase } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { PageHeader } from '@/components/common/PageHeader';
import { PriorityBadge, SeverityBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, TableSkeleton } from '@/components/common/States';
import { Pagination } from '@/components/common/Pagination';
import { StatCard } from '@/components/common/StatCard';
import { Skeleton } from '@/components/ui/misc';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtCurrency, fmtDateTime, fmtNumber, fmtPercent } from '@/lib/format';
import { AvailabilityControl } from './AvailabilityControl';

export default function HandlerClaimsPage({ view }: { view: 'open' | 'resolved' }) {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const profile = useAsync(() => api.handlerPortal.me(), []);
  const claims = useAsync(() => api.handlerPortal.claims(view, page, size), [view, page, size]);
  const h = profile.data?.handler;

  return (
    <>
      <PageHeader title={view === 'open' ? 'Assigned claims' : 'Resolved claims'}
        description={view === 'open'
          ? 'Claims a supervisor has assigned to you. Open a claim to see its details and mark it resolved when done.'
          : 'Claims you have resolved.'} />

      {view === 'open' && (
        <div className="mb-6 space-y-4">
          {profile.error ? <ErrorState error={profile.error} onRetry={profile.reload} compact /> :
            !h ? <Skeleton className="h-24" /> : (
              <>
                <AvailabilityControl handler={h} onChange={(p) => profile.setData(p)} />
                <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                  <StatCard icon={<Briefcase />} label="Open claims" value={`${h.currentWorkload} / ${h.maxWorkload}`} hint="Current workload / capacity" />
                  <StatCard icon={<CircleCheckBig />} tone="green" label="Resolved" value={fmtNumber(h.resolvedCount)} hint="In SmartAssign" />
                  <StatCard icon={<Gauge />} tone="blue" label="Resolved on time" value={h.hasHistory ? fmtPercent(h.successRate) : '—'}
                    hint={h.hasHistory ? `Within ${profile.data!.resolutionTargetDays} days of assignment` : 'No resolved claims yet'} />
                  <StatCard icon={<Timer />} tone="amber" label="Average resolution" value={h.hasHistory ? `${h.avgResolutionDays} days` : '—'}
                    hint={h.hasHistory ? 'From assignment to resolution' : 'No resolved claims yet'} />
                </div>
              </>
            )}
        </div>
      )}

      <Card>
        {claims.error ? <ErrorState error={claims.error} onRetry={claims.reload} /> :
          claims.loading && !claims.data ? <TableSkeleton cols={7} /> :
          claims.data!.totalElements === 0 ? (
            <EmptyState icon={view === 'open' ? <ListChecks /> : <ClipboardCheck />}
              title={view === 'open' ? 'No claims assigned to you right now' : 'You have not resolved any claims yet'}
              description={view === 'open' ? 'When a supervisor assigns you a claim it appears here.' : undefined} />
          ) : (
            <>
              <Table>
                <THead><TR>
                  <TH>Claim</TH><TH>Priority</TH><TH>Customer</TH><TH>Type</TH><TH>Severity</TH><TH className="text-right">Estimated loss</TH>
                  <TH>Assigned by</TH><TH>{view === 'open' ? 'Assigned' : 'Resolved'}</TH>
                </TR></THead>
                <TBody>
                  {claims.data!.content.map((c) => (
                    <TR key={c.id} interactive onClick={() => navigate(`/handler/claims/${c.id}`)}>
                      <TD>
                        <Link to={`/handler/claims/${c.id}`} className="font-medium text-ink hover:underline" onClick={(e) => e.stopPropagation()}>{c.claimNumber}</Link>
                        <p className="text-[12px] text-ink-subtle">{c.location}</p>
                      </TD>
                      <TD className="whitespace-nowrap"><PriorityBadge priority={c.priority.priority} /></TD>
                      <TD>{c.customer.fullName}</TD>
                      <TD>{CLAIM_TYPE_LABEL[c.claimType]}</TD>
                      <TD><SeverityBadge severity={c.severity} /></TD>
                      <TD className="text-right tabular">{fmtCurrency(c.estimatedLoss)}</TD>
                      <TD>{c.assignedBy?.fullName ?? '—'}</TD>
                      <TD className="whitespace-nowrap">{fmtDateTime(view === 'open' ? c.assignedAt : c.resolvedAt)}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
              <Pagination page={page} size={size} total={claims.data!.totalElements} totalPages={claims.data!.totalPages}
                onPage={setPage} onSize={(s) => { setSize(s); setPage(0); }} />
            </>
          )}
      </Card>
    </>
  );
}
