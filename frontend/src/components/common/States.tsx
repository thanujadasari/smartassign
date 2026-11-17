import type { ReactNode } from 'react';
import { CircleAlert, Inbox, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/misc';
import type { ApiError } from '@/lib/api';

export function EmptyState({ icon, title, description, action }:
  { icon?: ReactNode; title: string; description?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <div className="mb-3 flex size-11 items-center justify-center rounded-full bg-navy-50 text-navy-400 [&_svg]:size-5">
        {icon ?? <Inbox />}
      </div>
      <p className="text-[14px] font-semibold text-ink">{title}</p>
      {description && <p className="mt-1 max-w-md text-[13px] text-ink-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry, compact }: { error: ApiError | null; onRetry?: () => void; compact?: boolean }) {
  if (!error) return null;
  return (
    <div className={compact ? 'p-4' : 'px-6 py-12'}>
      <div className="mx-auto flex max-w-md flex-col items-center text-center">
        <div className="mb-3 flex size-11 items-center justify-center rounded-full bg-danger-soft text-danger">
          <CircleAlert className="size-5" />
        </div>
        <p className="text-[14px] font-semibold text-ink">We couldn't load this information</p>
        <p className="mt-1 text-[13px] text-ink-muted">{error.message}</p>
        {onRetry && (
          <Button variant="secondary" size="sm" className="mt-4" onClick={onRetry}>
            <RefreshCw /> Try again
          </Button>
        )}
      </div>
    </div>
  );
}

export function TableSkeleton({ rows = 6, cols = 6 }: { rows?: number; cols?: number }) {
  return (
    <div className="divide-y divide-border" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex items-center gap-4 px-4 py-3.5">
          {Array.from({ length: cols }).map((__, c) => (
            <Skeleton key={c} className="h-3.5" style={{ width: `${c === 0 ? 12 : 100 / cols}%` }} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function PageSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-8 w-72" />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-28" />)}
      </div>
      <Skeleton className="h-72" />
    </div>
  );
}
