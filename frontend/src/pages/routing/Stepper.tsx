import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export const STEPS = ['Select claim', 'Eligibility check', 'Recommendations', 'Supervisor decision'] as const;

export function Stepper({ current, completed }: { current: number; completed: number }) {
  return (
    <ol className="flex flex-wrap items-center gap-y-2 rounded-lg border border-border bg-surface px-4 py-3 shadow-card" aria-label="Routing progress">
      {STEPS.map((s, i) => {
        const done = i < completed;
        const active = i === current;
        return (
          <li key={s} className="flex items-center" aria-current={active ? 'step' : undefined}>
            <span className={cn('flex size-6 items-center justify-center rounded-full text-[11.5px] font-semibold tabular',
              done ? 'bg-success text-white' : active ? 'bg-navy-900 text-white' : 'bg-navy-100 text-ink-muted')}>
              {done ? <Check className="size-3.5" /> : i + 1}
            </span>
            <span className={cn('ml-2 text-[13px] font-medium', active ? 'text-ink' : done ? 'text-ink' : 'text-ink-subtle')}>{s}</span>
            {i < STEPS.length - 1 && <span className={cn('mx-3 h-px w-6 sm:w-10', done ? 'bg-success' : 'bg-border-strong')} aria-hidden />}
          </li>
        );
      })}
    </ol>
  );
}
