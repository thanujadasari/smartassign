import { Fragment, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ChevronDown, ChevronRight, Download, History, Search, ShieldOff, X } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input, Select } from '@/components/ui/input';
import { SortTH, Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { useToast } from '@/components/ui/toast';
import { PageHeader } from '@/components/common/PageHeader';
import { EmptyState, ErrorState, TableSkeleton } from '@/components/common/States';
import { Pagination } from '@/components/common/Pagination';
import { api, toApiError } from '@/lib/api';
import { CAN_EDIT, useAuth } from '@/lib/auth';
import { useAsync, useDebounced } from '@/lib/useAsync';
import { EVENT_LABEL } from '@/lib/labels';
import { fmtDateTime, titleCase } from '@/lib/format';

const EVENT_GROUPS: { label: string; values: string[] }[] = [
  { label: 'Assignment decisions', values: ['ASSIGNMENT_APPROVED', 'ASSIGNMENT_OVERRIDDEN', 'MANUAL_REVIEW_REQUESTED', 'FALLBACK_MANUAL_REVIEW'] },
  { label: 'Routing', values: ['ELIGIBILITY_CHECKED', 'RECOMMENDATIONS_GENERATED', 'ROUTING_NO_ELIGIBLE_HANDLER'] },
  { label: 'Claims', values: ['CLAIM_SUBMITTED', 'CLAIM_TRIAGED', 'CLAIM_RETRIAGED', 'CLAIM_PRIORITY_CHANGED', 'CLAIM_RESOLVED'] },
  { label: 'Handlers', values: ['HANDLER_CREATED', 'HANDLER_UPDATED', 'HANDLER_AVAILABILITY_CHANGED', 'HANDLER_STATUS_CHANGED'] },
  { label: 'Configuration & access', values: ['SETTINGS_WEIGHTS_UPDATED', 'ELIGIBILITY_RULE_UPDATED', 'USER_REGISTERED', 'STAFF_SIGNUP_REJECTED', 'LOGIN', 'LOGIN_FAILED', 'LOGOUT'] },
];

function eventTone(e: string): 'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  if (e === 'ASSIGNMENT_APPROVED') return 'success';
  if (e === 'ASSIGNMENT_OVERRIDDEN') return 'info';
  if (e.includes('MANUAL') || e.includes('NO_ELIGIBLE')) return 'warning';
  if (e.includes('DELETED') || e === 'LOGIN_FAILED') return 'danger';
  return 'neutral';
}

export default function AuditPage() {
  const { hasRole } = useAuth();
  if (!hasRole(...CAN_EDIT)) {
    return <EmptyState icon={<ShieldOff />} title="Restricted area" description="The assignment history and audit log is available to supervisors and administrators." />;
  }
  return <AuditLog />;
}

function AuditLog() {
  const [params, setParams] = useSearchParams();
  const { notify } = useToast();
  const [claim, setClaim] = useState(params.get('claimNumber') ?? '');
  const [handler, setHandler] = useState(params.get('handlerCode') ?? '');
  const [text, setText] = useState(params.get('q') ?? '');
  const dClaim = useDebounced(claim.trim());
  const dHandler = useDebounced(handler.trim());
  const dText = useDebounced(text.trim());
  const [eventType, setEventType] = useState(params.get('eventType') ?? '');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [sort, setSort] = useState('createdAt');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [open, setOpen] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);

  const filters = { claimNumber: dClaim, handlerCode: dHandler, eventType, from, to, q: dText };
  const { data, error, loading, reload } = useAsync(() => api.audit.list({ ...filters, sort, direction, page, size }),
    [dClaim, dHandler, eventType, from, to, dText, sort, direction, page, size]);
  const active = !!(claim || handler || text || eventType || from || to);
  const clear = () => { setClaim(''); setHandler(''); setText(''); setEventType(''); setFrom(''); setTo(''); setPage(0); setParams({}, { replace: true }); };
  const onSort = (f: string) => { setDirection(sort === f && direction === 'desc' ? 'asc' : 'desc'); setSort(f); setPage(0); };

  const exportCsv = async () => {
    setExporting(true);
    try {
      await api.audit.exportCsv(filters);
      notify('success', 'Audit export downloaded', 'The CSV contains the records matching the current filters.');
    } catch (e) {
      notify('error', 'Export failed', toApiError(e).message);
    } finally {
      setExporting(false);
    }
  };

  return (
    <>
      <PageHeader title="Assignment History & Audit" description="A permanent, append-only record of every recommendation, supervisor decision, override reason and configuration change — recorded by the server."
        actions={<Button variant="secondary" onClick={exportCsv} loading={exporting}><Download /> Export CSV</Button>} />

      <Card>
        <div className="grid gap-3 border-b border-border p-4 sm:grid-cols-2 lg:grid-cols-6">
          <div className="relative lg:col-span-2">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-subtle" />
            <Input value={text} onChange={(e) => { setText(e.target.value); setPage(0); }} placeholder="Search explanations and reasons" className="pl-9" aria-label="Search audit text" />
          </div>
          <Input value={claim} onChange={(e) => { setClaim(e.target.value); setPage(0); }} placeholder="Claim ID" aria-label="Filter by claim ID" />
          <Input value={handler} onChange={(e) => { setHandler(e.target.value); setPage(0); }} placeholder="Handler ID" aria-label="Filter by handler ID" />
          <Select value={eventType} onChange={(e) => { setEventType(e.target.value); setPage(0); }} aria-label="Filter by event" className="lg:col-span-2">
            <option value="">All events</option>
            {EVENT_GROUPS.map((g) => (
              <optgroup key={g.label} label={g.label}>
                {g.values.map((v) => <option key={v} value={v}>{EVENT_LABEL[v] ?? titleCase(v)}</option>)}
              </optgroup>
            ))}
          </Select>
          <label className="flex items-center gap-2 text-[12.5px] text-ink-muted">From
            <Input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} aria-label="From date" />
          </label>
          <label className="flex items-center gap-2 text-[12.5px] text-ink-muted">To
            <Input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} aria-label="To date" />
          </label>
          {active && <div className="flex items-center"><Button variant="ghost" size="sm" onClick={clear}><X /> Clear filters</Button></div>}
        </div>

        {error ? <ErrorState error={error} onRetry={reload} /> : loading && !data ? <TableSkeleton cols={7} /> :
          data && data.content.length === 0 ? <EmptyState icon={<History />} title="No audit records match" description={active ? 'Try widening the filters.' : 'Actions will be recorded here as they happen.'} /> : data && (
            <div className={loading ? 'opacity-60 transition-opacity' : undefined}>
              <Table>
                <THead>
                  <TR>
                    <TH className="w-8" />
                    <SortTH field="createdAt" sort={sort} direction={direction} onSort={onSort}>Time</SortTH>
                    <SortTH field="eventType" sort={sort} direction={direction} onSort={onSort}>Event</SortTH>
                    <SortTH field="claimNumber" sort={sort} direction={direction} onSort={onSort}>Claim</SortTH>
                    <TH>Recommended</TH>
                    <TH>Final handler</TH>
                    <SortTH field="recommendationScore" sort={sort} direction={direction} onSort={onSort} className="text-right">Score</SortTH>
                    <TH>Override / reason</TH>
                    <SortTH field="actorUsername" sort={sort} direction={direction} onSort={onSort}>User</SortTH>
                  </TR>
                </THead>
                <TBody>
                  {data.content.map((a) => (
                    <Fragment key={a.id}>
                      <TR interactive onClick={() => setOpen(open === a.id ? null : a.id)} aria-expanded={open === a.id}>
                        <TD className="pr-0">{open === a.id ? <ChevronDown className="size-4 text-ink-subtle" /> : <ChevronRight className="size-4 text-ink-subtle" />}</TD>
                        <TD className="whitespace-nowrap text-ink-muted tabular">{fmtDateTime(a.createdAt)}</TD>
                        <TD><Badge tone={eventTone(a.eventType)}>{EVENT_LABEL[a.eventType] ?? titleCase(a.eventType)}</Badge></TD>
                        <TD className="font-medium">{a.claimNumber ?? '—'}</TD>
                        <TD>{a.recommendedHandlerCode ?? '—'}</TD>
                        <TD>{a.finalHandlerCode ?? '—'}</TD>
                        <TD className="text-right tabular">{a.recommendationScore == null ? '—' : a.recommendationScore.toFixed(1)}</TD>
                        <TD className="max-w-[260px] truncate text-ink-muted" title={a.overrideReason ?? undefined}>{a.overrideReason ?? '—'}</TD>
                        <TD className="text-ink-muted">{a.actorUsername}</TD>
                      </TR>
                      {open === a.id && (
                        <tr className="bg-canvas/70">
                          <td />
                          <td colSpan={8} className="px-4 pb-4 pt-1">
                            <dl className="grid gap-3 text-[12.5px] md:grid-cols-2">
                              <Detail label="Supervisor action" value={a.supervisorAction ? titleCase(a.supervisorAction) : '—'} />
                              <Detail label="Eligibility" value={a.eligibilitySummary ?? '—'} />
                              <Detail label="Recorded entity" value={`${titleCase(a.entityType)} ${a.entityId ?? ''}`} />
                              <Detail label="Audit record" value={`#${a.id} · ${new Date(a.createdAt).toISOString()}`} />
                              {a.explanation && <Detail label="Explanation" value={a.explanation} wide />}
                              {a.overrideReason && <Detail label="Override reason" value={a.overrideReason} wide />}
                              {a.details && <Detail label="Details" value={<DetailsJson raw={a.details} />} wide />}
                              {a.claimNumber && <div className="md:col-span-2"><Link to={`/claims?q=${a.claimNumber}`} className="text-accent-600 hover:underline">Find claim {a.claimNumber}</Link></div>}
                            </dl>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </TBody>
              </Table>
              <Pagination page={data.page} size={data.size} total={data.totalElements} totalPages={data.totalPages} onPage={setPage} onSize={(s) => { setSize(s); setPage(0); }} />
            </div>
          )}
      </Card>
    </>
  );
}

function Detail({ label, value, wide }: { label: string; value: ReactNode; wide?: boolean }) {
  return (
    <div className={wide ? 'md:col-span-2' : undefined}>
      <dt className="text-[11.5px] uppercase tracking-wide text-ink-subtle">{label}</dt>
      <dd className="mt-0.5 text-ink">{value}</dd>
    </div>
  );
}

function DetailsJson({ raw }: { raw: string }) {
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>;
    return (
      <ul className="space-y-0.5">
        {Object.entries(obj).filter(([k]) => k !== 'rulesApplied').map(([k, v]) => (
          <li key={k}><span className="text-ink-muted">{titleCase(k.replace(/([A-Z])/g, '_$1'))}:</span> {typeof v === 'object' ? JSON.stringify(v) : String(v)}</li>
        ))}
      </ul>
    );
  } catch {
    return <>{raw}</>;
  }
}
