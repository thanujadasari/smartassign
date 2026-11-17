import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ClipboardCheck, Eye, Ellipsis, Search, Workflow, X } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Select } from '@/components/ui/input';
import { Tabs } from '@/components/ui/misc';
import { MenuItem, Popover } from '@/components/ui/dropdown';
import { SortTH, Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { PageHeader } from '@/components/common/PageHeader';
import { ClaimPriority, ClaimStatusBadge, SeverityBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, TableSkeleton } from '@/components/common/States';
import { Pagination } from '@/components/common/Pagination';
import { api } from '@/lib/api';
import { CAN_EDIT, useAuth } from '@/lib/auth';
import { useAsync, useDebounced } from '@/lib/useAsync';
import { useOptions } from '@/lib/options';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtCurrency, fmtDate, fmtNumber, titleCase } from '@/lib/format';

type StatusTab = 'ALL' | 'SUBMITTED' | 'UNASSIGNED' | 'MANUAL_REVIEW' | 'ASSIGNED' | 'RESOLVED';

export default function ClaimsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canEdit = hasRole(...CAN_EDIT);
  const options = useOptions();

  const status = (params.get('status') ?? 'ALL') as StatusTab;
  const claimType = params.get('claimType') ?? '';
  const severity = params.get('severity') ?? '';
  const location = params.get('location') ?? '';
  const sort = params.get('sort') ?? 'createdAt';
  const direction = (params.get('direction') ?? 'desc') as 'asc' | 'desc';
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 25);
  const [q, setQ] = useState(params.get('q') ?? '');
  const debouncedQ = useDebounced(q.trim());

  const update = (patch: Record<string, string | number | null>, resetPage = true) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '' || (k === 'status' && v === 'ALL')) next.delete(k);
      else next.set(k, String(v));
    }
    if (resetPage && !('page' in patch)) next.delete('page');
    setParams(next, { replace: true });
  };

  useEffect(() => {
    if ((params.get('q') ?? '') !== debouncedQ) update({ q: debouncedQ || null });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedQ]);

  const { data, error, loading, reload } = useAsync(
    () => api.claims.list({ q: params.get('q'), status: status === 'ALL' ? null : status, claimType, severity, location, sort, direction, page, size }),
    [params.toString()],
  );
  const counts = useAsync(() => api.dashboard.summary().then((d) => Object.fromEntries(d.claimsByStatus.map((s) => [s.label, s.value]))), []);

  const onSort = (f: string) => update({ sort: f, direction: sort === f && direction === 'asc' ? 'desc' : 'asc' });
  const filtersActive = !!(params.get('q') || claimType || severity || location);
  const total = counts.data ? Object.values(counts.data).reduce((a, b) => a + b, 0) : undefined;

  return (
    <>
      <PageHeader title="All Claims" description="Every claim registered by customers. New claims are triaged first, then routed to an eligible handler." />

      <Tabs<StatusTab> value={status} onChange={(v) => update({ status: v })} className="mb-4 flex-wrap"
        items={[
          { value: 'ALL', label: 'All claims', count: total },
          { value: 'SUBMITTED', label: 'Awaiting triage', count: counts.data?.SUBMITTED },
          { value: 'UNASSIGNED', label: 'Ready to assign', count: counts.data?.UNASSIGNED },
          { value: 'MANUAL_REVIEW', label: 'Manual review', count: counts.data?.MANUAL_REVIEW },
          { value: 'ASSIGNED', label: 'Assigned', count: counts.data?.ASSIGNED },
          { value: 'RESOLVED', label: 'Resolved', count: counts.data?.RESOLVED },
        ]} />

      <Card>
        <div className="flex flex-wrap items-center gap-3 border-b border-border p-4">
          <div className="relative w-full sm:w-64">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-subtle" />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Claim, policy or customer" className="pl-9" aria-label="Search by claim ID, policy number or customer name" />
          </div>
          <Select value={claimType} onChange={(e) => update({ claimType: e.target.value })} aria-label="Filter by line of business" className="w-full sm:w-48">
            <option value="">All lines of business</option>
            {options?.claimTypes.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </Select>
          <Select value={severity} onChange={(e) => update({ severity: e.target.value })} aria-label="Filter by severity" className="w-full sm:w-40">
            <option value="">All severities</option>
            {options?.severities.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </Select>
          <Select value={location} onChange={(e) => update({ location: e.target.value })} aria-label="Filter by location" className="w-full sm:w-40">
            <option value="">All locations</option>
            {options?.cities.map((c) => <option key={c} value={c}>{c}</option>)}
          </Select>
          {filtersActive && (
            <Button variant="ghost" size="sm" onClick={() => { setQ(''); update({ q: null, claimType: null, severity: null, location: null }); }}>
              <X /> Clear filters
            </Button>
          )}
        </div>

        {error ? <ErrorState error={error} onRetry={reload} /> : loading && !data ? <TableSkeleton cols={8} /> : data && data.content.length === 0 ? (
          <EmptyState title={filtersActive || status !== 'ALL' ? 'No claims match these filters' : 'No claims registered yet'}
            description={filtersActive || status !== 'ALL' ? 'Try a different search, or clear the filters.' : 'Claims appear here as soon as customers register them in the customer portal.'}
            action={filtersActive ? <Button variant="secondary" size="sm" onClick={() => { setQ(''); update({ q: null, claimType: null, severity: null, location: null }); }}>Clear filters</Button> : undefined} />
        ) : data && (
          <div className={loading ? 'opacity-60 transition-opacity' : undefined}>
            <Table>
              <THead>
                <TR>
                  <SortTH field="createdAt" sort={sort} direction={direction} onSort={onSort}>Claim</SortTH>
                  <TH>Customer</TH>
                  <SortTH field="claimType" sort={sort} direction={direction} onSort={onSort}>Line / policy</SortTH>
                  <SortTH field="priority" sort={sort} direction={direction} onSort={onSort}>Priority</SortTH>
                  <SortTH field="severity" sort={sort} direction={direction} onSort={onSort}>Severity</SortTH>
                  <SortTH field="location" sort={sort} direction={direction} onSort={onSort}>Location</SortTH>
                  <SortTH field="estimatedLoss" sort={sort} direction={direction} onSort={onSort} className="text-right">Est. loss</SortTH>
                  <SortTH field="fraudRiskScore" sort={sort} direction={direction} onSort={onSort}>Fraud risk</SortTH>
                  <SortTH field="status" sort={sort} direction={direction} onSort={onSort}>Status</SortTH>
                  <TH>Handler</TH>
                  <TH className="w-10"><span className="sr-only">Actions</span></TH>
                </TR>
              </THead>
              <TBody>
                {data.content.map((c) => (
                  <TR key={c.id} interactive onClick={() => navigate(`/claims/${c.id}`)}>
                    <TD>
                      <Link to={`/claims/${c.id}`} onClick={(e) => e.stopPropagation()} className="whitespace-nowrap font-medium text-accent-600 hover:underline">{c.claimNumber}</Link>
                      <p className="whitespace-nowrap text-[12px] text-ink-subtle">{fmtDate(c.createdAt)}</p>
                    </TD>
                    <TD className="whitespace-nowrap">{c.customer.fullName}</TD>
                    <TD>
                      <p className="whitespace-nowrap">{CLAIM_TYPE_LABEL[c.claimType]}</p>
                      <p className="whitespace-nowrap text-[12px] text-ink-subtle">{titleCase(c.policyType)}{c.complexity ? ` · ${titleCase(c.complexity)} complexity` : ''}</p>
                    </TD>
                    <TD className="whitespace-nowrap"><ClaimPriority p={c.priority} /></TD>
                    <TD><SeverityBadge severity={c.severity} /></TD>
                    <TD>{c.location}</TD>
                    <TD className="text-right tabular">{fmtCurrency(c.estimatedLoss)}</TD>
                    <TD><FraudRisk value={c.fraudRiskScore} /></TD>
                    <TD><ClaimStatusBadge status={c.status} /></TD>
                    <TD className="whitespace-nowrap text-ink-muted">{c.assignedHandler?.fullName ?? '—'}</TD>
                    <TD onClick={(e) => e.stopPropagation()}>
                      <Popover label={`Actions for ${c.claimNumber}`} trigger={({ toggle, open }) => (
                        <Button variant="ghost" size="icon-sm" onClick={toggle} aria-expanded={open} aria-label={`Actions for ${c.claimNumber}`}><Ellipsis /></Button>
                      )}>
                        {(close) => (
                          <>
                            <MenuItem icon={<Eye />} onClick={() => { close(); navigate(`/claims/${c.id}`); }}>View details</MenuItem>
                            {canEdit && (c.status === 'UNASSIGNED' || c.status === 'MANUAL_REVIEW') && (
                              <MenuItem icon={<Workflow />} onClick={() => { close(); navigate(`/routing?claimId=${c.id}`); }}>Open in routing</MenuItem>
                            )}
                            {canEdit && c.status === 'SUBMITTED' && (
                              <MenuItem icon={<ClipboardCheck />} onClick={() => { close(); navigate(`/claims/${c.id}`); }}>Triage claim</MenuItem>
                            )}
                          </>
                        )}
                      </Popover>
                    </TD>
                  </TR>
                ))}
              </TBody>
            </Table>
            <Pagination page={data.page} size={data.size} total={data.totalElements} totalPages={data.totalPages}
              onPage={(p) => update({ page: p }, false)} onSize={(s) => update({ size: s })} />
          </div>
        )}
      </Card>

    </>
  );
}

function FraudRisk({ value }: { value: number | null }) {
  if (value == null) return <span className="text-[12.5px] text-ink-subtle">—</span>;
  const tone = value >= 0.7 ? 'bg-danger' : value >= 0.4 ? 'bg-amber-500' : 'bg-success';
  return (
    <span className="inline-flex items-center gap-2 tabular">
      <span className={`size-2 rounded-full ${tone}`} aria-hidden />
      {fmtNumber(Math.round(value * 100))}%
    </span>
  );
}
