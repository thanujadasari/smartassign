import type { HTMLAttributes, ReactNode } from 'react';
import { TriangleAlert, CircleCheck, Info, CircleX } from 'lucide-react';
import { cn } from '@/lib/utils';

export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('animate-pulse rounded-md bg-navy-100/70', className)} {...props} />;
}

export function Separator({ className }: { className?: string }) {
  return <div role="separator" className={cn('h-px w-full bg-border', className)} />;
}

export function Progress({ value, tone = 'navy', className, label }:
  { value: number; tone?: 'navy' | 'success' | 'warning' | 'danger' | 'blue'; className?: string; label?: string }) {
  const pct = Math.max(0, Math.min(100, value));
  const color = { navy: 'bg-navy-800', success: 'bg-success', warning: 'bg-amber-500', danger: 'bg-danger', blue: 'bg-accent-500' }[tone];
  return (
    <div role="progressbar" aria-valuenow={Math.round(pct)} aria-valuemin={0} aria-valuemax={100} aria-label={label}
      className={cn('h-1.5 w-full overflow-hidden rounded-full bg-navy-100', className)}>
      <div className={cn('h-full rounded-full transition-[width] duration-500', color)} style={{ width: `${pct}%` }} />
    </div>
  );
}

const ALERT_TONES = {
  info: { box: 'bg-info-soft border-info-border text-navy-800', icon: Info, iconColor: 'text-info' },
  success: { box: 'bg-success-soft border-success-border text-green-900', icon: CircleCheck, iconColor: 'text-success' },
  warning: { box: 'bg-warning-soft border-warning-border text-amber-900', icon: TriangleAlert, iconColor: 'text-warning' },
  danger: { box: 'bg-danger-soft border-danger-border text-red-900', icon: CircleX, iconColor: 'text-danger' },
};

export function Alert({ tone = 'info', title, children, className, action }:
  { tone?: keyof typeof ALERT_TONES; title?: ReactNode; children?: ReactNode; className?: string; action?: ReactNode }) {
  const t = ALERT_TONES[tone];
  const Icon = t.icon;
  return (
    <div role={tone === 'danger' || tone === 'warning' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-lg border px-4 py-3 text-[13px]', t.box, className)}>
      <Icon className={cn('mt-0.5 size-4 shrink-0', t.iconColor)} aria-hidden />
      <div className="min-w-0 flex-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={cn(title && 'mt-0.5', 'leading-relaxed')}>{children}</div>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

export function Tabs<T extends string>({ value, onChange, items, className }:
  { value: T; onChange: (v: T) => void; items: { value: T; label: ReactNode; count?: number }[]; className?: string }) {
  return (
    <div role="tablist" className={cn('inline-flex max-w-full flex-wrap items-center gap-1 rounded-lg bg-navy-50 p-1', className)}>
      {items.map((it) => (
        <button key={it.value} type="button" role="tab" aria-selected={value === it.value} onClick={() => onChange(it.value)}
          className={cn('inline-flex cursor-pointer items-center gap-1.5 rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors',
            value === it.value ? 'bg-surface text-ink shadow-card' : 'text-ink-muted hover:text-ink')}>
          {it.label}
          {it.count != null && (
            <span className={cn('rounded-full px-1.5 text-[11px] tabular',
              value === it.value ? 'bg-navy-900 text-white' : 'bg-navy-100 text-ink-muted')}>{it.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}
