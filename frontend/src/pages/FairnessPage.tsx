import { useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { OctagonAlert, Gauge, Info, RefreshCw, Scale, Sigma, TriangleAlert, Users } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select } from '@/components/ui/input';
import { Alert, Tabs } from '@/components/ui/misc';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { PageHeader } from '@/components/common/PageHeader';
import { StatCard } from '@/components/common/StatCard';
import { HandlerStatusBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, PageSkeleton } from '@/components/common/States';
import { UtilizationBar } from '@/components/common/Score';
import { axisProps, gridProps, legendProps, tooltipProps } from '@/components/charts/ChartTooltip';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { CHART, CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtNumber, fmtPercent } from '@/lib/format';
import { Link } from 'react-router-dom';

type HandlerFilter = 'ALL' | 'FLAGGED' | 'HIGH_UTILIZATION' | 'AT_CAPACITY' | 'OVER_CAPACITY';

export default function FairnessPage() {
  const [windowDays, setWindowDays] = useState(30);
  const { data, error, loading, reload } = useAsync(() => api.fairness.summary(windowDays), [windowDays]);
  const [filter, setFilter] = useState<HandlerFilter>('FLAGGED');

  const flagged = useMemo(() => new Set(data?.report.warnings.map((w) => w.handlerCode).filter(Boolean) as string[]), [data]);
  if (loading && !data) return <PageSkeleton />;
  if (error && !data) return <ErrorState error={error} onRetry={reload} />;
  if (!data) return null;
  const r = data.report;
  const groupChart = r.groups.map((g) => ({ name: g.label, utilisation: Math.round(g.averageUtilization * 1000) / 10, variation: Math.round(g.utilizationCoefficientOfVariation * 100) / 100 }));
  const decisions = Object.keys(data.assignmentsByExpertise).map((k) => ({
    name: k, assignments: data.assignmentsByExpertise[k], recommendations: data.topRecommendationsByExpertise[k] ?? 0,
  }));
  const hasDecisions = decisions.some((d) => d.assignments + d.recommendations > 0);
  const handlers = r.handlers.filter((h) => filter === 'ALL' ? true : filter === 'FLAGGED' ? flagged.has(h.handlerCode) : h.status === filter);

  return (
    <>
      <PageHeader title="Fairness Monitoring" description="Workload balance across handlers, judged against each handler's capacity and line of expertise."
        actions={<>
          <Select value={windowDays} onChange={(e) => setWindowDays(Number(e.target.value))} aria-label="Monitoring window" className="w-40">
            <option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
          </Select>
          <Button variant="secondary" onClick={reload} loading={loading}><RefreshCw /> Refresh</Button>
        </>} />

      <Alert tone="info" className="mb-6">
        {data.note} Equal claim counts are not the goal: handlers with more capacity or scarcer expertise can legitimately receive more work.
      </Alert>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <StatCard label="Average utilisation" value={fmtPercent(r.averageUtilization)} icon={<Gauge />} hint={`${r.availableHandlers} active & available handlers`} />
        <StatCard label="Utilisation spread" value={r.utilizationStdDev.toFixed(3)} icon={<Sigma />} tone="blue" hint="standard deviation · lower is more even" />
        <StatCard label={`High utilisation (≥${Math.round(r.highUtilizationThreshold * 100)}%)`} value={r.highUtilizationHandlers} icon={<TriangleAlert />} tone="amber" />
        <StatCard label="At capacity" value={r.atCapacityHandlers} icon={<Users />} tone="amber" hint="cannot receive new claims" />
        <StatCard label="Over capacity" value={r.overCapacityHandlers} icon={<OctagonAlert />} tone={r.overCapacityHandlers ? 'red' : 'green'} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-1">
          <CardHeader title="Indicators for review" icon={<Scale />} description={`${r.warnings.length} indicator(s) · not automatic findings`} />
          {r.warnings.length === 0 ? <EmptyState title="No imbalance indicators" description="Workload is within the configured thresholds." /> : (
            <ul className="max-h-[420px] divide-y divide-border overflow-y-auto">
              {r.warnings.map((w, i) => (
                <li key={i} className="flex gap-3 px-5 py-3">
                  <Badge tone={w.severity === 'CRITICAL' ? 'danger' : w.severity === 'WARNING' ? 'warning' : 'info'} className="h-fit">
                    {w.severity === 'CRITICAL' ? 'Critical' : w.severity === 'WARNING' ? 'Review' : 'Info'}
                  </Badge>
                  <div className="min-w-0">
                    <p className="text-[13px] text-ink">{w.message}</p>
                    <p className="mt-0.5 text-[11.5px] text-ink-subtle">{[w.handlerCode, w.group && CLAIM_TYPE_LABEL[w.group]].filter(Boolean).join(' · ')}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader title="Utilisation distribution" description="Active handlers by share of capacity" />
          <CardBody className="h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.utilizationDistribution} margin={{ top: 8, right: 4, left: -18, bottom: 0 }}>
                <CartesianGrid {...gridProps} />
                <XAxis dataKey="label" {...axisProps} />
                <YAxis allowDecimals={false} {...axisProps} />
                <Tooltip {...tooltipProps} formatter={(v) => [fmtNumber(Number(v)), 'Handlers']} />
                <Bar dataKey="handlers" radius={[4, 4, 0, 0]} maxBarSize={44}>
                  {data.utilizationDistribution.map((b) => <Cell key={b.label} fill={b.label === 'Over capacity' ? CHART.red : b.label === 'Full' ? CHART.amber : b.label === '75-99%' ? CHART.blue : CHART.navy} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Utilisation by line of expertise" description="Average utilisation (%) per expertise group" />
          <CardBody className="h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={groupChart} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 0 }}>
                <CartesianGrid {...gridProps} horizontal={false} vertical />
                <XAxis type="number" domain={[0, 100]} {...axisProps} />
                <YAxis type="category" dataKey="name" width={118} {...axisProps} />
                <Tooltip {...tooltipProps} formatter={(v) => [`${Number(v).toFixed(1)}%`, 'Avg. utilisation']} />
                <Bar dataKey="utilisation" fill={CHART.navy} radius={[0, 4, 4, 0]} maxBarSize={22} />
              </BarChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Expertise groups" description="Capacity, workload and evenness within each line of expertise" />
          <Table>
            <THead><TR><TH>Expertise</TH><TH className="text-right">Handlers</TH><TH className="text-right">Load / capacity</TH><TH className="text-right">Avg. util.</TH><TH className="text-right">Variation</TH><TH className="text-right">Assigned ({data.windowDays}d)</TH></TR></THead>
            <TBody>
              {r.groups.map((g) => (
                <TR key={g.expertise}>
                  <TD className="whitespace-nowrap font-medium">{g.label}</TD>
                  <TD className="text-right tabular">{g.handlers}</TD>
                  <TD className="text-right tabular">{g.totalWorkload} / {g.totalCapacity}</TD>
                  <TD className="text-right tabular">{fmtPercent(g.averageUtilization)}</TD>
                  <TD className="whitespace-nowrap text-right">
                    <span className="tabular">{g.utilizationCoefficientOfVariation.toFixed(2)}</span>
                    {g.utilizationCoefficientOfVariation > 0.5 && g.handlers >= 3 && <Badge tone="warning" className="ml-2">Uneven</Badge>}
                  </TD>
                  <TD className="text-right tabular">{g.assignmentsInWindow}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
          <p className="flex items-start gap-1.5 border-t border-border px-5 py-2.5 text-[11.5px] text-ink-subtle">
            <Info className="mt-0.5 size-3.5 shrink-0" /> Variation is the coefficient of variation of utilisation (standard deviation ÷ mean) among active, available handlers. Above 0.5 is flagged for review.
          </p>
        </Card>
        <Card>
          <CardHeader title="Assignments & recommendations" description={`By expertise, last ${data.windowDays} days`} />
          <CardBody className="h-[300px]">
            {!hasDecisions ? <EmptyState title="No routing activity in this window" /> : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={decisions} margin={{ top: 8, right: 4, left: -18, bottom: 0 }}>
                  <CartesianGrid {...gridProps} />
                  <XAxis dataKey="name" {...axisProps} tickFormatter={(v: string) => v.split(' ')[0]} />
                  <YAxis allowDecimals={false} {...axisProps} />
                  <Tooltip {...tooltipProps} />
                  <Legend {...legendProps} />
                  <Bar dataKey="recommendations" name="Top recommendations" fill={CHART.sky} radius={[4, 4, 0, 0]} maxBarSize={28} />
                  <Bar dataKey="assignments" name="Assignments" fill={CHART.navy} radius={[4, 4, 0, 0]} maxBarSize={28} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardBody>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader title="Handler workload" description="Expected assignments are proportional to each handler's share of capacity within their expertise group" />
        <div className="border-b border-border px-5 py-3">
          <Tabs value={filter} onChange={setFilter} className="flex-wrap" items={[
            { value: 'FLAGGED', label: 'Flagged', count: flagged.size }, { value: 'HIGH_UTILIZATION', label: 'High utilisation' },
            { value: 'AT_CAPACITY', label: 'At capacity' }, { value: 'OVER_CAPACITY', label: 'Over capacity' }, { value: 'ALL', label: 'All handlers', count: r.handlers.length },
          ]} />
        </div>
        {handlers.length === 0 ? <EmptyState title="No handlers in this view" /> : (
          <div className="max-h-[520px] overflow-y-auto">
            <Table>
              <THead><TR><TH>Handler</TH><TH>Expertise</TH><TH>Location</TH><TH>Workload</TH><TH className="text-right">Assignments</TH><TH className="text-right">Expected by capacity</TH><TH className="text-right">Top recommendations</TH><TH>Status</TH></TR></THead>
              <TBody>
                {handlers.map((h) => (
                  <TR key={h.handlerCode}>
                    <TD className="whitespace-nowrap"><Link to={`/handlers?q=${h.handlerCode}`} className="font-medium hover:text-accent-600 hover:underline">{h.name}</Link> <span className="text-[12px] text-ink-subtle">{h.handlerCode}</span></TD>
                    <TD>{CLAIM_TYPE_LABEL[h.expertise] ?? h.expertise}</TD>
                    <TD>{h.location}</TD>
                    <TD><UtilizationBar current={h.currentWorkload} max={h.maxWorkload} /></TD>
                    <TD className="text-right tabular">{h.assignmentsInWindow}</TD>
                    <TD className="text-right tabular text-ink-muted">{h.expectedAssignmentsByCapacity.toFixed(1)}</TD>
                    <TD className="text-right tabular">{h.topRecommendationsInWindow}</TD>
                    <TD><HandlerStatusBadge status={h.status} /></TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </div>
        )}
      </Card>
    </>
  );
}
