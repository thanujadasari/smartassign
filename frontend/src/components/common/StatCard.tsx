import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function StatCard({ label, value, icon, hint, tone = 'navy', onClick }: {
  label: string; value: ReactNode; icon: ReactNode; hint?: ReactNode;
  tone?: 'navy' | 'blue' | 'green' | 'amber' | 'red'; onClick?: () => void;
}) {
  const tones = {
    navy: 'bg-navy-50 text-navy-700',
    blue: 'bg-accent-50 text-accent-600',
    green: 'bg-success-soft text-success',
    amber: 'bg-warning-soft text-warning',
    red: 'bg-danger-soft text-danger',
  };
  const Comp = onClick ? 'button' : 'div';
  return (
    <Comp type={onClick ? 'button' : undefined} onClick={onClick}
      className={cn('group flex h-full flex-col justify-start rounded-lg border border-border bg-surface p-4 text-left shadow-card',
        onClick && 'cursor-pointer transition-colors hover:border-navy-300')}>
      <div className="flex w-full items-start justify-between gap-3">
        <p className="text-[12.5px] font-medium text-ink-muted">{label}</p>
        <span className={cn('flex size-8 items-center justify-center rounded-md [&_svg]:size-4', tones[tone])}>{icon}</span>
      </div>
      <p className="mt-2 text-[24px] font-semibold tracking-tight text-ink tabular">{value}</p>
      {hint && <p className="mt-1 text-[12px] text-ink-subtle">{hint}</p>}
    </Comp>
  );
}
