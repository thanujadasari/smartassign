import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { fmtNumber } from '@/lib/format';

export function Pagination({ page, size, total, totalPages, onPage, onSize }: {
  page: number; size: number; total: number; totalPages: number; onPage: (p: number) => void; onSize?: (s: number) => void;
}) {
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3 text-[12.5px] text-ink-muted">
      <span className="tabular">Showing {fmtNumber(from)}–{fmtNumber(to)} of {fmtNumber(total)}</span>
      <div className="flex items-center gap-3">
        {onSize && (
          <label className="flex items-center gap-2">
            Rows
            <select value={size} onChange={(e) => onSize(Number(e.target.value))} aria-label="Rows per page"
              className="h-8 rounded-md border border-border-strong bg-surface px-2 text-[12.5px] text-ink">
              {[10, 25, 50, 100].map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
        )}
        <span className="tabular">Page {totalPages === 0 ? 0 : page + 1} of {fmtNumber(totalPages)}</span>
        <div className="flex gap-1">
          <Button variant="secondary" size="icon-sm" aria-label="Previous page" disabled={page <= 0} onClick={() => onPage(page - 1)}>
            <ChevronLeft />
          </Button>
          <Button variant="secondary" size="icon-sm" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
            <ChevronRight />
          </Button>
        </div>
      </div>
    </div>
  );
}
