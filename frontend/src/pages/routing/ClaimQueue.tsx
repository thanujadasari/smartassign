import { useState } from 'react';
import { Search } from 'lucide-react';
import { Card, CardHeader } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Tabs } from '@/components/ui/misc';
import { PriorityBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, TableSkeleton } from '@/components/common/States';
import { Pagination } from '@/components/common/Pagination';
import { api } from '@/lib/api';
import { useAsync, useDebounced } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtCurrency } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { Claim } from '@/lib/types';

/** Work queue of claims that can be routed: awaiting assignment or in manual review. */
export function ClaimQueue({ selectedId, onSelect, version }: { selectedId: number | null; onSelect: (c: Claim) => void; version: number }) {
  const [status, setStatus] = useState<'UNASSIGNED' | 'MANUAL_REVIEW'>('UNASSIGNED');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const dq = useDebounced(q.trim());
  const { data, error, loading, reload } = useAsync(
    () => api.claims.list({ status, q: dq, page, size: 12, sort: 'priority', direction: 'asc' }),
    [status, dq, page, version],
  );

  return (
    <Card className="flex flex-col">
      <CardHeader title="Claims queue" description="Select a claim to route" />
      <div className="space-y-3 border-b border-border p-3">
        <Tabs value={status} onChange={(v) => { setStatus(v); setPage(0); }} className="w-full [&>button]:flex-1"
          items={[{ value: 'UNASSIGNED', label: 'Ready to assign' }, { value: 'MANUAL_REVIEW', label: 'Manual review' }]} />
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-subtle" />
          <Input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} placeholder="Search claim ID" className="pl-9" aria-label="Search claims queue" />
        </div>
      </div>
      {error ? <ErrorState error={error} onRetry={reload} compact /> : loading && !data ? <TableSkeleton rows={8} cols={2} /> :
        data && data.content.length === 0 ? <EmptyState title={status === 'UNASSIGNED' ? 'No triaged claims waiting' : 'Manual review queue is empty'} /> : data && (
          <>
            <ul className="divide-y divide-border" role="listbox" aria-label="Claims available for routing">
              {data.content.map((c) => (
                <li key={c.id}>
                  <button type="button" role="option" aria-selected={selectedId === c.id} onClick={() => onSelect(c)}
                    className={cn('flex w-full cursor-pointer items-center justify-between gap-3 px-4 py-3 text-left transition-colors',
                      selectedId === c.id ? 'bg-accent-50 shadow-[inset_3px_0_0_var(--color-accent-500)]' : 'hover:bg-navy-50/60')}>
                    <span className="min-w-0">
                      <span className="block text-[13px] font-semibold text-ink">{c.claimNumber}</span>
                      <span className="block truncate text-[12px] text-ink-muted">{CLAIM_TYPE_LABEL[c.claimType]} · {c.location} · {fmtCurrency(c.estimatedLoss)}</span>
                    </span>
                    <PriorityBadge priority={c.priority.priority} />
                  </button>
                </li>
              ))}
            </ul>
            <Pagination page={data.page} size={data.size} total={data.totalElements} totalPages={data.totalPages} onPage={setPage} />
          </>
        )}
    </Card>
  );
}
