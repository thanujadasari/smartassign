import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Award, BriefcaseBusiness, CircleSlash, Clock3, History, Layers, MapPin, Pencil, Power } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Alert, Progress } from '@/components/ui/misc';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { StatCard } from '@/components/common/StatCard';
import { HandlerStatusBadge, SeverityBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, PageSkeleton } from '@/components/common/States';
import { api } from '@/lib/api';
import { CAN_EDIT, useAuth } from '@/lib/auth';
import { useAsync } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtDateTime, fmtPercent, initials } from '@/lib/format';
import { HandlerEditDialog } from './HandlerEditDialog';
import { HandlerActionDialogs, type HandlerAction } from './HandlerActions';

export default function HandlerDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canEdit = hasRole(...CAN_EDIT);
  const { data, error, loading, reload } = useAsync(() => api.handlers.get(id), [id]);
  const [editOpen, setEditOpen] = useState(false);
  const [action, setAction] = useState<HandlerAction>(null);

  if (loading && !data) return <PageSkeleton />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (!data) return null;
  const h = data.handler;
  const util = h.maxWorkload ? h.currentWorkload / h.maxWorkload : 1;

  return (
    <>
      <Link to="/handlers" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-ink-muted hover:text-ink">
        <ArrowLeft className="size-4" /> Back to handler directory
      </Link>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-center gap-4">
          <span className="flex size-14 items-center justify-center rounded-full bg-navy-900 text-[18px] font-semibold text-white">{initials(h.fullName)}</span>
          <div>
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="text-[22px] font-semibold tracking-tight text-ink">{h.fullName}</h1>
              <HandlerStatusBadge status={h.status} />
            </div>
            <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[13.5px] text-ink-muted">
              <span>Handler {h.code}</span>
              <span className="flex items-center gap-1"><MapPin className="size-3.5" />{h.location}</span>
              <span className="flex items-center gap-1"><BriefcaseBusiness className="size-3.5" />{h.primaryExpertiseLabel}</span>
              {h.email && <span>{h.email}</span>}
              {h.phone && <span>{h.phone}</span>}
            </p>
          </div>
        </div>
        {canEdit && (
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setAction({ kind: 'status', handler: h })}><Power /> {h.active ? 'Deactivate' : 'Activate'}</Button>
            <Button onClick={() => setEditOpen(true)}><Pencil /> Edit capacity & expertise</Button>
          </div>
        )}
      </div>

      {!h.available && (
        <Alert tone="warning" className="mb-6" title={`Unavailable since ${fmtDateTime(h.availabilityChangedAt)}`}>
          {h.unavailableReason ?? 'No reason recorded.'} — set by the handler. They receive no new claims until they mark themselves available.
        </Alert>
      )}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Current workload" value={`${h.currentWorkload} / ${h.maxWorkload}`} icon={<Layers />} tone={util > 1 ? 'red' : util >= 0.85 ? 'amber' : 'navy'}
          hint={`${h.openSlots} open slot(s) · ${fmtPercent(util)} of capacity`} />
        <StatCard label="Resolved on time" value={h.hasHistory ? fmtPercent(h.successRate) : '—'} icon={<Award />} tone="green"
          hint={h.hasHistory ? 'Of claims resolved in SmartAssign' : 'No resolved claims yet'} />
        <StatCard label="Average resolution time" value={h.hasHistory ? `${Number(h.avgResolutionDays).toFixed(1)} days` : '—'} icon={<Clock3 />} tone="blue"
          hint={h.hasHistory ? 'From assignment to resolution' : 'No resolved claims yet'} />
        <StatCard label="Claims resolved" value={h.resolvedCount} icon={<BriefcaseBusiness />} hint={`${h.experienceYears} years of experience`} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Open claims" description="Claims currently assigned to this handler through SmartAssign" />
          {data.openClaims.length === 0 ? (
            <EmptyState title="No open claims" />
          ) : (
            <Table>
              <THead><TR><TH>Claim</TH><TH>Line</TH><TH>Severity</TH><TH>Location</TH><TH>Assigned by</TH><TH>Assigned</TH></TR></THead>
              <TBody>
                {data.openClaims.map((c) => (
                  <TR key={c.claimId} interactive onClick={() => navigate(`/claims/${c.claimId}`)}>
                    <TD className="font-medium text-accent-600">{c.claimNumber}</TD>
                    <TD>{CLAIM_TYPE_LABEL[c.claimType] ?? c.claimType}</TD>
                    <TD><SeverityBadge severity={c.severity} /></TD>
                    <TD>{c.location}</TD>
                    <TD>{c.assignedByName ?? '—'}</TD>
                    <TD className="whitespace-nowrap text-ink-muted">{fmtDateTime(c.assignedAt)}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          )}
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader title="Capacity" />
            <CardBody className="space-y-3">
              <Progress value={util * 100} tone={util > 1 ? 'danger' : util >= 0.85 ? 'warning' : 'navy'} label="Workload utilisation" />
              <div className="flex justify-between text-[12.5px] text-ink-muted"><span>{fmtPercent(util)} utilised</span><span>max {h.maxWorkload} claims</span></div>
              <div className="grid grid-cols-2 gap-2 pt-2 text-[12.5px]">
                <div className="rounded-md bg-canvas p-2.5"><p className="text-ink-muted">Availability</p><p className="font-medium text-ink">{h.available ? 'Available' : 'Unavailable'}</p></div>
                <div className="rounded-md bg-canvas p-2.5"><p className="text-ink-muted">Employment</p><p className="font-medium text-ink">{h.active ? 'Active' : 'Inactive'}</p></div>
              </div>
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Skills" description="Claim lines this handler is eligible for" />
            <CardBody className="flex flex-wrap gap-2">
              {h.skills.map((s) => <Badge key={s} tone={s === h.primaryExpertise ? 'navy' : 'neutral'}>{CLAIM_TYPE_LABEL[s]}</Badge>)}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Availability history" icon={<History />} description="Set by the handler" />
            {data.availabilityHistory.length === 0 ? <CardBody><p className="text-[13px] text-ink-muted">No changes since registration.</p></CardBody> : (
              <ul className="divide-y divide-border">
                {data.availabilityHistory.slice(0, 8).map((e, i) => (
                  <li key={i} className="px-5 py-2.5 text-[12.5px]">
                    <p className="flex items-center gap-1.5 font-medium text-ink">
                      {e.available ? 'Available' : <><CircleSlash className="size-3.5 text-warning" /> Unavailable</>}
                      <span className="ml-auto font-normal text-ink-subtle">{fmtDateTime(e.changedAt)}</span>
                    </p>
                    {e.reason && <p className="mt-0.5 text-ink-muted">{e.reason}</p>}
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card>
            <CardHeader title="Routing history" />
            <CardBody><dl className="space-y-2.5 text-[13px]">
              <div className="flex justify-between"><dt className="text-ink-muted">Assignments received</dt><dd className="font-medium tabular">{data.totalAssignments}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">…of which via override</dt><dd className="font-medium tabular">{data.overridesReceived}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">Times top recommendation</dt><dd className="font-medium tabular">{data.recommendationsAsTopChoice}</dd></div>
            </dl></CardBody>
          </Card>
        </div>
      </div>

      <HandlerEditDialog open={editOpen} handler={h} onClose={() => setEditOpen(false)} onSaved={reload} />
      <HandlerActionDialogs action={action} onClose={() => setAction(null)} onDone={reload} />
    </>
  );
}
