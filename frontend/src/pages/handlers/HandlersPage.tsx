import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Eye, Ellipsis, Pencil, Power, Search, Users, X } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input, Select } from '@/components/ui/input';
import { Tabs } from '@/components/ui/misc';
import { MenuItem, Popover } from '@/components/ui/dropdown';
import { SortTH, Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { PageHeader } from '@/components/common/PageHeader';
import { HandlerStatusBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, TableSkeleton } from '@/components/common/States';
import { Pagination } from '@/components/common/Pagination';
import { UtilizationBar } from '@/components/common/Score';
import { api } from '@/lib/api';
import { CAN_EDIT, useAuth } from '@/lib/auth';
import { useAsync, useDebounced } from '@/lib/useAsync';
import { useOptions } from '@/lib/options';
import { fmtPercent, initials } from '@/lib/format';
import type { Handler } from '@/lib/types';
import { HandlerEditDialog } from './HandlerEditDialog';
import { HandlerActionDialogs, type HandlerAction } from './HandlerActions';

type StatusTab = 'ALL' | 'AVAILABLE' | 'AT_CAPACITY' | 'UNAVAILABLE' | 'INACTIVE';

export default function HandlersPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canEdit = hasRole(...CAN_EDIT);
  const options = useOptions();

  const status = (params.get('status') ?? 'ALL') as StatusTab;
  const expertise = params.get('expertise') ?? '';
  const location = params.get('location') ?? '';
  const sort = params.get('sort') ?? 'code';
  const direction = (params.get('direction') ?? 'asc') as 'asc' | 'desc';
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
    () => api.handlers.list({ q: params.get('q'), status: status === 'ALL' ? null : status, expertise, location, sort, direction, page, size }),
    [params.toString()],
  );
  const [editing, setEditing] = useState<Handler | null>(null);
  const [action, setAction] = useState<HandlerAction>(null);
  const onSort = (f: string) => update({ sort: f, direction: sort === f && direction === 'asc' ? 'desc' : 'asc' });
  const filtersActive = !!(params.get('q') || expertise || location);
  const clear = () => { setQ(''); update({ q: null, expertise: null, location: null }); };

  return (
    <>
      <PageHeader title="Handler Directory" description="Handlers who registered through staff sign-up. Set their capacity and expertise here; availability is set by each handler." />

      <Tabs<StatusTab> value={status} onChange={(v) => update({ status: v })} className="mb-4 flex-wrap" items={[
        { value: 'ALL', label: 'All handlers' }, { value: 'AVAILABLE', label: 'Available' }, { value: 'AT_CAPACITY', label: 'At capacity' },
        { value: 'UNAVAILABLE', label: 'Unavailable' }, { value: 'INACTIVE', label: 'Inactive' },
      ]} />

      <Card>
        <div className="flex flex-wrap items-center gap-3 border-b border-border p-4">
          <div className="relative w-full sm:w-64">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-subtle" />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name or handler ID" className="pl-9" aria-label="Search handlers" />
          </div>
          <Select value={expertise} onChange={(e) => update({ expertise: e.target.value })} aria-label="Filter by expertise" className="w-full sm:w-52">
            <option value="">All expertise</option>
            {options?.expertise.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </Select>
          <Select value={location} onChange={(e) => update({ location: e.target.value })} aria-label="Filter by location" className="w-full sm:w-40">
            <option value="">All locations</option>
            {options?.cities.map((c) => <option key={c} value={c}>{c}</option>)}
          </Select>
          {filtersActive && <Button variant="ghost" size="sm" onClick={clear}><X /> Clear filters</Button>}
        </div>

        {error ? <ErrorState error={error} onRetry={reload} /> : loading && !data ? <TableSkeleton cols={8} /> : data && data.content.length === 0 ? (
          <EmptyState icon={<Users />} title={filtersActive || status !== 'ALL' ? 'No handlers match these filters' : 'No handlers have registered yet'}
            description={filtersActive || status !== 'ALL' ? undefined : 'Handlers appear here after they sign up on the staff sign-up page with the staff registration code.'}
            action={filtersActive ? <Button variant="secondary" size="sm" onClick={clear}>Clear filters</Button> : undefined} />
        ) : data && (
          <div className={loading ? 'opacity-60 transition-opacity' : undefined}>
            <Table>
              <THead>
                <TR>
                  <SortTH field="fullName" sort={sort} direction={direction} onSort={onSort}>Handler</SortTH>
                  <SortTH field="primaryExpertise" sort={sort} direction={direction} onSort={onSort}>Expertise</SortTH>
                  <SortTH field="currentWorkload" sort={sort} direction={direction} onSort={onSort}>Workload</SortTH>
                  <SortTH field="location" sort={sort} direction={direction} onSort={onSort}>Location</SortTH>
                  <SortTH field="resolvedCount" sort={sort} direction={direction} onSort={onSort}>Track record</SortTH>
                  <TH>Status</TH>
                  <TH className="w-10"><span className="sr-only">Actions</span></TH>
                </TR>
              </THead>
              <TBody>
                {data.content.map((h) => (
                  <TR key={h.id} interactive onClick={() => navigate(`/handlers/${h.id}`)}>
                    <TD>
                      <div className="flex items-center gap-3">
                        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-navy-100 text-[11.5px] font-semibold text-navy-700">{initials(h.fullName)}</span>
                        <div className="min-w-0">
                          <Link to={`/handlers/${h.id}`} onClick={(e) => e.stopPropagation()} className="whitespace-nowrap font-medium text-ink hover:text-accent-600 hover:underline">{h.fullName}</Link>
                          <p className="text-[12px] text-ink-subtle">{h.code}</p>
                        </div>
                      </div>
                    </TD>
                    <TD>
                      {h.primaryExpertise === 'MULTI_P_AND_C' ? <Badge tone="navy">{h.primaryExpertiseLabel}</Badge> : <Badge>{h.primaryExpertiseLabel}</Badge>}
                      <p className="mt-1 text-[12px] text-ink-subtle tabular">{h.experienceYears} yrs experience</p>
                    </TD>
                    <TD><UtilizationBar current={h.currentWorkload} max={h.maxWorkload} /></TD>
                    <TD>{h.location}</TD>
                    <TD className="whitespace-nowrap tabular">
                      {h.hasHistory ? (
                        <>
                          <p>{h.resolvedCount} resolved · {fmtPercent(h.successRate)} on time</p>
                          <p className="text-[12px] text-ink-subtle">{Number(h.avgResolutionDays).toFixed(1)} days average</p>
                        </>
                      ) : <p className="text-ink-subtle">No resolved claims yet</p>}
                    </TD>
                    <TD>
                      <HandlerStatusBadge status={h.status} />
                      {!h.available && h.unavailableReason && <p className="mt-1 max-w-[180px] truncate text-[12px] text-ink-subtle" title={h.unavailableReason}>{h.unavailableReason}</p>}
                    </TD>
                    <TD onClick={(e) => e.stopPropagation()}>
                      <Popover label={`Actions for ${h.fullName}`} trigger={({ toggle, open }) => (
                        <Button variant="ghost" size="icon-sm" onClick={toggle} aria-expanded={open} aria-label={`Actions for ${h.fullName}`}><Ellipsis /></Button>
                      )}>
                        {(close) => (
                          <>
                            <MenuItem icon={<Eye />} onClick={() => { close(); navigate(`/handlers/${h.id}`); }}>View profile & workload</MenuItem>
                            {canEdit && <>
                              <MenuItem icon={<Pencil />} onClick={() => { close(); setEditing(h); }}>Edit capacity & expertise</MenuItem>
                              <MenuItem icon={<Power />} onClick={() => { close(); setAction({ kind: 'status', handler: h }); }}>{h.active ? 'Deactivate' : 'Activate'}</MenuItem>
                            </>}
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
      <HandlerEditDialog open={!!editing} handler={editing} onClose={() => setEditing(null)} onSaved={reload} />
      <HandlerActionDialogs action={action} onClose={() => setAction(null)} onDone={reload} />
    </>
  );
}
