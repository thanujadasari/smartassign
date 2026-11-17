import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  Activity, ArrowRight, CircleCheck, CircleSlash, ClipboardCheck, ClipboardList, Cloud, Database, Gauge, Hourglass, Plug,
  RefreshCw, ShieldCheck, UserCheck, Workflow,
} from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Button, buttonVariants } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { PageHeader } from '@/components/common/PageHeader';
import { StatCard } from '@/components/common/StatCard';
import { ClaimStatusBadge, SeverityBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, PageSkeleton } from '@/components/common/States';
import { axisProps, gridProps, legendProps, tooltipProps } from '@/components/charts/ChartTooltip';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { CHART, CLAIM_STATUS_LABEL, CLAIM_TYPE_LABEL, EVENT_LABEL } from '@/lib/labels';
import { fmtCurrency, fmtNumber, fmtPercent, fmtRelative } from '@/lib/format';
import { useAuth } from '@/lib/auth';
import { cn } from '@/lib/utils';

const STATUS_COLORS: Record<string, string> = {
  SUBMITTED: CHART.sky, UNASSIGNED: CHART.blue, ASSIGNED: CHART.green, MANUAL_REVIEW: CHART.amber, RESOLVED: CHART.slate,
};

export default function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { data, error, loading, reload } = useAsync(() => api.dashboard.summary(), []);

  if (loading && !data) return <PageSkeleton />;
  if (error && !data) return <ErrorState error={error} onRetry={reload} />;
  if (!data) return null;
  const { kpis, system } = data;
  const statusData = data.claimsByStatus.filter((s) => s.value > 0);
  const trend = data.assignmentTrend.map((t) => ({
    ...t, day: new Date(t.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }),
  }));
  const trendTotal = trend.reduce((s, t) => s + t.approved + t.overridden + t.manualReview, 0);

  return (
    <>
      <PageHeader
        title="Claims Overview"
        description={`Good ${greeting()}, ${user?.fullName.split(' ')[0] ?? ''}. Here is the current state of claim intake, handler capacity and assignment activity.`}
        actions={
          <>
            <Button variant="secondary" onClick={reload} loading={loading} aria-label="Refresh dashboard"><RefreshCw /> Refresh</Button>
            <Link to="/triage" className={buttonVariants({ variant: 'secondary' })}><ClipboardCheck /> Triage queue</Link>
            <Link to="/routing" className={buttonVariants()}><Workflow /> Route a claim</Link>
          </>
        }
      />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Awaiting triage" value={fmtNumber(kpis.awaitingTriage)} icon={<ClipboardCheck />} tone="amber" hint="New customer claims" onClick={() => navigate('/triage')} />
        <StatCard label="Ready to assign" value={fmtNumber(kpis.readyToAssign)} icon={<Hourglass />} tone="blue" hint="Triaged, not yet routed" onClick={() => navigate('/claims?status=UNASSIGNED')} />
        <StatCard label="Assigned claims" value={fmtNumber(kpis.assignedClaims)} icon={<CircleCheck />} tone="green" hint={`${fmtNumber(kpis.resolvedClaims)} resolved · ${fmtNumber(kpis.totalClaims)} total`} onClick={() => navigate('/claims?status=ASSIGNED')} />
        <StatCard label="Manual review" value={fmtNumber(kpis.manualReviewClaims)} icon={<ClipboardList />} tone="amber" onClick={() => navigate('/claims?status=MANUAL_REVIEW')} />
        <StatCard label="Available handlers" value={fmtNumber(kpis.availableHandlers)} icon={<UserCheck />}
          hint={`of ${fmtNumber(kpis.activeHandlers)} active · ${fmtNumber(kpis.unavailableHandlers)} unavailable`} onClick={() => navigate('/handlers?status=AVAILABLE')} />
        <StatCard label="Eligible assignment rate" value={kpis.eligibleAssignmentRate == null ? '—' : fmtPercent(kpis.eligibleAssignmentRate, 1)} icon={<ShieldCheck />} tone="green"
          hint={kpis.recommendationRunsLast30Days ? `${fmtNumber(kpis.recommendationRunsLast30Days)} routing runs in 30 days` : 'No routing runs yet'} />
        <StatCard label="Average handler workload" value={fmtPercent(kpis.averageHandlerUtilization)} icon={<Gauge />} tone={kpis.averageHandlerUtilization > 0.85 ? 'red' : 'navy'} hint="of maximum capacity, active handlers" />
        <StatCard label="Decisions · last 7 days" value={fmtNumber(kpis.assignmentsLast7Days)} icon={<Activity />} hint="approvals, overrides & reviews" onClick={() => navigate('/audit')} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card>
          <CardHeader title="Claims by status" description="Current position of every claim" />
          <CardBody>
            <div className="relative h-[220px]">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={statusData} dataKey="value" nameKey="label" innerRadius={62} outerRadius={92} paddingAngle={2} stroke="none">
                    {statusData.map((s) => <Cell key={s.label} fill={STATUS_COLORS[s.label] ?? CHART.sky} />)}
                  </Pie>
                  <Tooltip {...tooltipProps} formatter={(v, n) => [fmtNumber(Number(v)), CLAIM_STATUS_LABEL[String(n)] ?? String(n)]} />
                </PieChart>
              </ResponsiveContainer>
              <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                <span className="text-[22px] font-semibold text-ink tabular">{fmtNumber(kpis.totalClaims)}</span>
                <span className="text-[11.5px] text-ink-subtle">claims</span>
              </div>
            </div>
            <ul className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2">
              {data.claimsByStatus.map((s) => (
                <li key={s.label} className="flex items-center justify-between gap-2 text-[12.5px]">
                  <span className="flex items-center gap-2 text-ink-muted">
                    <span className="size-2 rounded-full" style={{ background: STATUS_COLORS[s.label] }} />{CLAIM_STATUS_LABEL[s.label] ?? s.label}
                  </span>
                  <span className="font-medium text-ink tabular">{fmtNumber(s.value)}</span>
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Handler workload distribution" description="Active handlers by share of maximum capacity" />
          <CardBody>
            <div className="h-[272px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.workloadDistribution} margin={{ top: 8, right: 4, left: -18, bottom: 0 }}>
                  <CartesianGrid {...gridProps} />
                  <XAxis dataKey="label" {...axisProps} />
                  <YAxis allowDecimals={false} {...axisProps} />
                  <Tooltip {...tooltipProps} formatter={(v) => [fmtNumber(Number(v)), 'Handlers']} />
                  <Bar dataKey="value" radius={[4, 4, 0, 0]} maxBarSize={44}>
                    {data.workloadDistribution.map((b) => (
                      <Cell key={b.label} fill={b.label === 'Over capacity' ? CHART.red : b.label === 'Full' ? CHART.amber : b.label === '75-99%' ? CHART.blue : CHART.navy} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Assignment activity" description="Supervisor decisions over the last 14 days" />
          <CardBody>
            {trendTotal === 0 ? (
              <div className="flex h-[272px] items-center">
                <EmptyState icon={<Activity />} title="No decisions yet" description="Approvals, overrides and manual reviews will appear here."
                  action={<Link to="/routing" className={buttonVariants({ size: 'sm' })}>Route a claim</Link>} />
              </div>
            ) : (
              <div className="h-[272px]">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={trend} margin={{ top: 8, right: 4, left: -18, bottom: 0 }}>
                    <CartesianGrid {...gridProps} />
                    <XAxis dataKey="day" {...axisProps} interval="preserveStartEnd" minTickGap={18} />
                    <YAxis allowDecimals={false} {...axisProps} />
                    <Tooltip {...tooltipProps} />
                    <Legend {...legendProps} />
                    <Area type="monotone" dataKey="approved" name="Approved" stackId="1" stroke={CHART.navy} fill={CHART.navy} fillOpacity={0.85} />
                    <Area type="monotone" dataKey="overridden" name="Overridden" stackId="1" stroke={CHART.blue} fill={CHART.blue} fillOpacity={0.75} />
                    <Area type="monotone" dataKey="manualReview" name="Manual review" stackId="1" stroke={CHART.amber} fill={CHART.amber} fillOpacity={0.7} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Recent claims" description="Most recently updated claim records"
            action={<Link to="/claims" className={buttonVariants({ variant: 'link', size: 'sm' })}>View all <ArrowRight /></Link>} />
          {data.recentClaims.length === 0 ? <EmptyState title="No claims yet" /> : (
            <Table>
              <THead><TR><TH>Claim</TH><TH>Line</TH><TH>Severity</TH><TH>Location</TH><TH className="text-right">Est. loss</TH><TH>Status</TH><TH>Handler</TH></TR></THead>
              <TBody>
                {data.recentClaims.map((c) => (
                  <TR key={c.id} interactive onClick={() => navigate(`/claims/${c.id}`)}>
                    <TD><Link to={`/claims/${c.id}`} className="font-medium text-accent-600 hover:underline" onClick={(e) => e.stopPropagation()}>{c.claimNumber}</Link></TD>
                    <TD>{CLAIM_TYPE_LABEL[c.claimType]}</TD>
                    <TD><SeverityBadge severity={c.severity} /></TD>
                    <TD className="text-ink-muted">{c.location}</TD>
                    <TD className="text-right tabular">{fmtCurrency(c.estimatedLoss)}</TD>
                    <TD><ClaimStatusBadge status={c.status} /></TD>
                    <TD className="text-ink-muted">{c.assignedHandler ? `${c.assignedHandler.fullName}` : '—'}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          )}
        </Card>

        <Card>
          <CardHeader title="Recent supervisor actions" action={<Link to="/audit" className={buttonVariants({ variant: 'link', size: 'sm' })}>Audit log <ArrowRight /></Link>} />
          {data.recentActions.length === 0 ? (
            <EmptyState icon={<Activity />} title="No recent activity" description="Routing decisions and changes will be listed here." />
          ) : (
            <ol className="divide-y divide-border">
              {data.recentActions.map((a) => (
                <li key={a.id} className="flex gap-3 px-5 py-3">
                  <span className={cn('mt-1.5 size-2 shrink-0 rounded-full',
                    a.eventType.includes('OVERRIDDEN') ? 'bg-accent-500' : a.eventType.includes('MANUAL') ? 'bg-amber-500' :
                    a.eventType.includes('APPROVED') ? 'bg-success' : 'bg-navy-300')} aria-hidden />
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] font-medium text-ink">{EVENT_LABEL[a.eventType] ?? a.eventType}</p>
                    <p className="truncate text-[12px] text-ink-muted">
                      {[a.claimNumber, a.finalHandlerCode && `→ ${a.finalHandlerCode}`, `by ${a.actorUsername}`].filter(Boolean).join(' · ')}
                    </p>
                  </div>
                  <span className="shrink-0 text-[11.5px] text-ink-subtle">{fmtRelative(a.createdAt)}</span>
                </li>
              ))}
            </ol>
          )}
        </Card>
      </div>

      {data.unavailableHandlers.length > 0 && (
        <Card className="mt-6">
          <CardHeader title="Handlers currently unavailable" icon={<CircleSlash />} description="Set by the handlers themselves - they receive no new claims"
            action={<Link to="/handlers?status=UNAVAILABLE" className={buttonVariants({ variant: 'link', size: 'sm' })}>All handlers <ArrowRight /></Link>} />
          <ul className="divide-y divide-border">
            {data.unavailableHandlers.map((h) => (
              <li key={h.id} className="flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-[13px]">
                <Link to={`/handlers/${h.id}`} className="font-medium text-ink hover:underline">{h.fullName} <span className="font-normal text-ink-subtle">· {h.code}</span></Link>
                <span className="min-w-0 flex-1 text-ink-muted sm:text-right">{h.reason ?? 'No reason recorded'}</span>
                <span className="text-[12px] text-ink-subtle">{fmtRelative(h.since)}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card className="mt-6">
        <CardHeader title="System status" icon={<Database />} />
        <CardBody className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <SourceItem icon={<Plug />} label="Guidewire ClaimCenter" value={system.claimCenter === 'NOT_CONNECTED' ? 'Not connected' : 'Configured — not verified'}
            sub="Claims come from the customer portal; no live ClaimCenter connection" tone="neutral" />
          <SourceItem icon={<Cloud />} label="Predicted claim fit"
            value={{ ONLINE: 'Online', OFFLINE: 'Offline', DISABLED: 'Switched off' }[system.suitabilityEngine]}
            sub={system.suitabilityEngine === 'ONLINE' ? 'Contributing to recommendations' : 'Recommendations use profile factors only'}
            tone={system.suitabilityEngine === 'ONLINE' ? 'success' : 'warning'} />
          <SourceItem icon={<ShieldCheck />} label="Operational database" value={system.database === 'ONLINE' ? 'Online' : system.database}
            sub="Claims, triage, assignments, availability and audit history" tone="success" />
        </CardBody>
      </Card>
    </>
  );
}

function SourceItem({ icon, label, value, sub, tone }:
  { icon: ReactNode; label: string; value: string; sub: string; tone: 'success' | 'warning' | 'neutral' }) {
  return (
    <div className="flex gap-3 rounded-lg border border-border p-3.5">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-navy-50 text-navy-600 [&_svg]:size-4">{icon}</span>
      <div className="min-w-0">
        <p className="text-[12px] text-ink-muted">{label}</p>
        <div className="mt-0.5"><Badge tone={tone} dot>{value}</Badge></div>
        <p className="mt-1.5 text-[11.5px] leading-snug text-ink-subtle">{sub}</p>
      </div>
    </div>
  );
}

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'morning' : h < 17 ? 'afternoon' : 'evening';
}
