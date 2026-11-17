import { cn } from '@/lib/utils';
import type { Factor } from '@/lib/types';

export function scoreTone(score: number) {
  if (score >= 75) return { ring: '#15803d', text: 'text-success', label: 'Strong match' };
  if (score >= 60) return { ring: '#3b7ddd', text: 'text-accent-600', label: 'Good match' };
  if (score >= 45) return { ring: '#d97706', text: 'text-warning', label: 'Fair match' };
  return { ring: '#8693a3', text: 'text-ink-muted', label: 'Weak match' };
}

/** Circular 0-100 suitability gauge. */
export function ScoreRing({ score, size = 56 }: { score: number; size?: number }) {
  const r = (size - 8) / 2;
  const c = 2 * Math.PI * r;
  const t = scoreTone(score);
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }} role="img" aria-label={`Suitability ${score.toFixed(0)} out of 100`}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#e3e9f3" strokeWidth="5" />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={t.ring} strokeWidth="5" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - Math.max(0, Math.min(100, score)) / 100)}
          style={{ transition: 'stroke-dashoffset 600ms ease-out' }} />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-[15px] font-semibold text-ink tabular">
        {score.toFixed(0)}
      </span>
    </div>
  );
}

const FACTOR_COLORS: Record<string, string> = {
  EXPERTISE: '#0f1f3a',
  WORKLOAD: '#34507f',
  EXPERIENCE: '#47679b',
  SUCCESS_RATE: '#3b7ddd',
  SIMILAR_CLAIMS: '#6b88b6',
  PROXIMITY: '#9cb1d2',
  ML_SUITABILITY: '#15803d',
};

/** Stacked bar showing how each factor contributed points to the 0-100 score. */
export function FactorStack({ factors, className }: { factors: Factor[]; className?: string }) {
  return (
    <div className={cn('flex h-2 w-full overflow-hidden rounded-full bg-navy-100', className)} aria-hidden>
      {factors.filter((f) => f.points > 0).map((f) => (
        <div key={f.factor} style={{ width: `${f.points}%`, background: FACTOR_COLORS[f.factor] ?? '#9cb1d2' }} />
      ))}
    </div>
  );
}

/** Factor-by-factor breakdown table used in the explanation panel. */
export function FactorBreakdown({ factors }: { factors: Factor[] }) {
  return (
    <ul className="space-y-2.5">
      {factors.map((f) => (
        <li key={f.factor}>
          <div className="flex items-baseline justify-between gap-3 text-[12.5px]">
            <span className="flex items-center gap-2 font-medium text-ink">
              <span className="size-2 rounded-sm" style={{ background: FACTOR_COLORS[f.factor] ?? '#9cb1d2' }} aria-hidden />
              {f.label}
              <span className="font-normal text-ink-subtle">· weight {f.weight.toFixed(0)}%</span>
            </span>
            <span className="tabular font-semibold text-ink">+{f.points.toFixed(1)}</span>
          </div>
          <div className="mt-1 flex items-center gap-3">
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-navy-100">
              <div className="h-full rounded-full" style={{ width: `${f.value * 100}%`, background: FACTOR_COLORS[f.factor] ?? '#9cb1d2' }} />
            </div>
            <span className="w-9 text-right text-[11.5px] text-ink-subtle tabular">{Math.round(f.value * 100)}%</span>
          </div>
          <p className="mt-0.5 text-[12px] text-ink-muted">{f.detail}</p>
        </li>
      ))}
    </ul>
  );
}

export function UtilizationBar({ current, max }: { current: number; max: number }) {
  const u = max > 0 ? current / max : 1;
  const tone = u > 1 ? 'bg-danger' : u >= 0.85 ? 'bg-amber-500' : u >= 0.6 ? 'bg-accent-500' : 'bg-success';
  return (
    <div className="flex min-w-28 items-center gap-2">
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-navy-100">
        <div className={cn('h-full rounded-full', tone)} style={{ width: `${Math.min(100, u * 100)}%` }} />
      </div>
      <span className="w-12 text-right text-[12px] text-ink-muted tabular">{current}/{max}</span>
    </div>
  );
}
