import { useState } from 'react';
import { CircleCheck, ChevronDown, ChevronRight, ShieldCheck, CircleX } from 'lucide-react';
import { Card, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Tabs } from '@/components/ui/misc';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { EmptyState } from '@/components/common/States';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtPercent } from '@/lib/format';
import type { EligibilityRow, ExclusionCount } from '@/lib/types';
import { cn } from '@/lib/utils';

export function ExclusionBreakdown({ items, total }: { items: ExclusionCount[]; total: number }) {
  if (!items.length) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {items.map((e) => (
        <span key={e.ruleCode} className="inline-flex items-center gap-1.5 rounded-md border border-border bg-canvas px-2.5 py-1 text-[12px] text-ink-muted">
          <CircleX className="size-3.5 text-danger" aria-hidden />{e.ruleName}
          <span className="font-semibold text-ink tabular">{e.handlers}</span>
          <span className="text-ink-subtle">/ {total}</span>
        </span>
      ))}
    </div>
  );
}

export function EligibilityPanel({ eligible, ineligible, rulesApplied, breakdown }: {
  eligible: EligibilityRow[]; ineligible: EligibilityRow[]; breakdown: ExclusionCount[];
  rulesApplied: { code: string; name: string }[];
}) {
  const [tab, setTab] = useState<'eligible' | 'excluded'>(eligible.length ? 'eligible' : 'excluded');
  const [open, setOpen] = useState<number | null>(null);
  const rows = tab === 'eligible' ? eligible : ineligible;
  const total = eligible.length + ineligible.length;

  return (
    <Card>
      <CardHeader title="Hard eligibility results" icon={<ShieldCheck />}
        description="Every handler is checked against every mandatory rule. Only handlers that pass all rules can be ranked or assigned." />
      <div className="space-y-4 border-b border-border px-5 py-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-lg border border-success-border bg-success-soft p-3">
            <p className="text-[12px] font-medium text-success">Eligible</p>
            <p className="text-[22px] font-semibold text-ink tabular">{eligible.length}</p>
          </div>
          <div className="rounded-lg border border-danger-border bg-danger-soft p-3">
            <p className="text-[12px] font-medium text-danger">Excluded</p>
            <p className="text-[22px] font-semibold text-ink tabular">{ineligible.length}</p>
          </div>
          <div className="rounded-lg border border-border bg-canvas p-3">
            <p className="text-[12px] font-medium text-ink-muted">Rules applied</p>
            <p className="mt-1 text-[12px] leading-snug text-ink">{rulesApplied.map((r) => r.name).join(' · ')}</p>
          </div>
        </div>
        <ExclusionBreakdown items={breakdown} total={total} />
      </div>
      <div className="px-5 pt-4">
        <Tabs value={tab} onChange={(v) => { setTab(v); setOpen(null); }}
          items={[{ value: 'eligible', label: 'Eligible handlers', count: eligible.length }, { value: 'excluded', label: 'Excluded handlers', count: ineligible.length }]} />
      </div>
      {rows.length === 0 ? (
        <EmptyState title={tab === 'eligible' ? 'No eligible handlers' : 'No handlers were excluded'} />
      ) : (
        <div className="mt-3 max-h-[420px] overflow-y-auto">
          <Table>
            <THead>
              <TR><TH className="w-8" /><TH>Handler</TH><TH>Expertise</TH><TH>Location</TH><TH>Workload</TH><TH>{tab === 'eligible' ? 'Result' : 'Exclusion reason'}</TH></TR>
            </THead>
            <TBody>
              {rows.map((r) => (
                <FragmentRow key={r.handler.id} row={r} open={open === r.handler.id} onToggle={() => setOpen(open === r.handler.id ? null : r.handler.id)} />
              ))}
            </TBody>
          </Table>
        </div>
      )}
    </Card>
  );
}

function FragmentRow({ row, open, onToggle }: { row: EligibilityRow; open: boolean; onToggle: () => void }) {
  const h = row.handler;
  return (
    <>
      <TR interactive onClick={onToggle} aria-expanded={open}>
        <TD className="pr-0">{open ? <ChevronDown className="size-4 text-ink-subtle" /> : <ChevronRight className="size-4 text-ink-subtle" />}</TD>
        <TD><span className="font-medium">{h.fullName}</span> <span className="text-[12px] text-ink-subtle">{h.code}</span></TD>
        <TD>{CLAIM_TYPE_LABEL[h.primaryExpertise] ?? h.expertiseLabel}</TD>
        <TD>{h.location}</TD>
        <TD className="tabular">{h.currentWorkload}/{h.maxWorkload}</TD>
        <TD>
          {row.eligible ? <Badge tone="success"><CircleCheck /> Eligible — all rules passed</Badge> :
            <span className="text-[12.5px] text-danger">Excluded — {row.exclusionReasons}</span>}
        </TD>
      </TR>
      {open && (
        <tr className="bg-canvas/70">
          <td />
          <td colSpan={5} className="px-4 pb-4 pt-1">
            <ul className="grid gap-1.5 sm:grid-cols-2">
              {row.rules.map((rule) => (
                <li key={rule.code} className={cn('flex items-start gap-2 rounded-md border px-3 py-2 text-[12.5px]',
                  rule.passed ? 'border-success-border bg-surface' : 'border-danger-border bg-danger-soft')}>
                  {rule.passed ? <CircleCheck className="mt-0.5 size-3.5 shrink-0 text-success" /> : <CircleX className="mt-0.5 size-3.5 shrink-0 text-danger" />}
                  <span><span className="font-medium text-ink">{rule.name}:</span> <span className="text-ink-muted">{rule.reason}</span></span>
                </li>
              ))}
            </ul>
            <p className="mt-2 text-[11.5px] text-ink-subtle">
              {h.hasHistory ? `${fmtPercent(h.successRate)} resolved on time · ${h.similarClaimsHandled} claims resolved` : 'New handler - no resolved claims yet'} · {h.experienceYears} yrs experience
            </p>
          </td>
        </tr>
      )}
    </>
  );
}
