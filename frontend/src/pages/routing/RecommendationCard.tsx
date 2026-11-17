import { useState } from 'react';
import { Award, ChevronDown, ChevronUp, MapPin, Scale, Sparkles } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { FactorBreakdown, FactorStack, ScoreRing, scoreTone } from '@/components/common/Score';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtPercent } from '@/lib/format';
import type { Recommendation } from '@/lib/types';
import { cn } from '@/lib/utils';

export function RecommendationCard({ rec, top, selected, onSelect, disabled, defaultOpen }: {
  rec: Recommendation; top: boolean; selected: boolean; onSelect?: () => void; disabled?: boolean; defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(!!defaultOpen);
  const h = rec.handler;
  const tone = scoreTone(rec.suitabilityScore);
  return (
    <article className={cn('rounded-lg border bg-surface shadow-card transition-colors',
      top ? 'border-navy-300 ring-1 ring-navy-200' : 'border-border', selected && 'border-accent-500 ring-2 ring-accent-500/30')}
      aria-label={`Rank ${rec.rank}: ${h.fullName}`}>
      {top && (
        <div className="flex items-center gap-1.5 rounded-t-lg bg-navy-900 px-4 py-1.5 text-[11.5px] font-semibold uppercase tracking-wide text-white">
          <Sparkles className="size-3.5 text-accent-200" /> Recommended handler
        </div>
      )}
      <div className="flex flex-wrap items-start gap-4 p-4">
        <div className="flex size-8 items-center justify-center rounded-md bg-navy-50 text-[13px] font-semibold text-navy-700 tabular" aria-hidden>#{rec.rank}</div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-[14.5px] font-semibold text-ink">{h.fullName}</h3>
            <span className="text-[12px] text-ink-subtle">{h.code}</span>
            {rec.sameCity && <Badge tone="success"><MapPin /> Same city</Badge>}
            {rec.fairnessAdjusted && <Badge tone="warning"><Scale /> Workload balance applied</Badge>}
          </div>
          <p className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[12.5px] text-ink-muted">
            <span>{CLAIM_TYPE_LABEL[h.primaryExpertise]}</span>
            <span>{h.location}{!rec.sameCity && ` · ${Math.round(rec.distanceKm)} km`}</span>
            <span>{h.currentWorkload}/{h.maxWorkload} open claims</span>
            <span><Award className="mr-0.5 inline size-3" />{h.hasHistory ? `${fmtPercent(h.successRate)} on time` : 'No resolved claims yet'}</span>
            <span>{h.experienceYears} yrs</span>
          </p>
          <p className="mt-2.5 text-[13px] leading-relaxed text-ink">{rec.explanation}</p>
          <div className="mt-3 flex items-center gap-3">
            <FactorStack factors={rec.factors} className="max-w-sm" />
            <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open}
              className="inline-flex shrink-0 cursor-pointer items-center gap-1 text-[12.5px] font-medium text-accent-600 hover:underline">
              {open ? <>Hide score breakdown <ChevronUp className="size-3.5" /></> : <>View score breakdown <ChevronDown className="size-3.5" /></>}
            </button>
          </div>
        </div>
        <div className="flex flex-col items-center gap-1">
          <ScoreRing score={rec.suitabilityScore} />
          <span className={cn('text-[11.5px] font-medium', tone.text)}>{tone.label}</span>
        </div>
      </div>
      {open && (
        <div className="border-t border-border bg-canvas/50 px-4 py-4 sm:px-16">
          <FactorBreakdown factors={rec.factors} />
          {rec.fairnessAdjusted && (
            <p className="mt-3 text-[12px] text-warning">
              Factor total {rec.scoreBeforeFairness.toFixed(1)} reduced to {rec.suitabilityScore.toFixed(1)} by the workload fairness control.
            </p>
          )}
        </div>
      )}
      {onSelect && !top && (
        <div className="flex justify-end border-t border-border px-4 py-2.5">
          <Button size="sm" variant={selected ? 'primary' : 'secondary'} onClick={onSelect} disabled={disabled}>
            {selected ? 'Selected' : 'Select this handler instead'}
          </Button>
        </div>
      )}
    </article>
  );
}
