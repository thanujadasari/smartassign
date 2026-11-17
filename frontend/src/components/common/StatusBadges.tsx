import { Badge } from '@/components/ui/badge';
import { CLAIM_STATUS_LABEL, HANDLER_STATUS_LABEL } from '@/lib/labels';

export function ClaimStatusBadge({ status, label }: { status: string; label?: string }) {
  const tone = {
    SUBMITTED: 'warning', UNASSIGNED: 'info', IN_REVIEW: 'info', ASSIGNED: 'success', MANUAL_REVIEW: 'warning',
    RESOLVED: 'neutral',
  }[status] as
    'info' | 'success' | 'warning' | 'neutral' | undefined;
  return <Badge tone={tone ?? 'neutral'} dot>{label ?? CLAIM_STATUS_LABEL[status] ?? status}</Badge>;
}

/** HIGH / MEDIUM / LOW. `suggested` marks a rule suggestion that the supervisor has not confirmed yet. */
export function PriorityBadge({ priority, suggested }: { priority: string | null | undefined; suggested?: boolean }) {
  if (!priority) return <span className="text-[12.5px] text-ink-subtle">—</span>;
  const tone = ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'neutral' } as const)[priority as 'HIGH'] ?? 'neutral';
  const label = priority.charAt(0) + priority.slice(1).toLowerCase();
  return (
    <Badge tone={tone} dot title={suggested ? 'Suggested by the rules - not yet confirmed by a supervisor' : undefined}>
      {label}{suggested ? ' (suggested)' : ''} priority
    </Badge>
  );
}

/** The decided priority, or the suggestion until a supervisor triages the claim. */
export function ClaimPriority({ p }: { p: { priority: string | null; suggestedPriority: string | null } }) {
  return p.priority ? <PriorityBadge priority={p.priority} /> : <PriorityBadge priority={p.suggestedPriority} suggested />;
}

export function SeverityBadge({ severity }: { severity: string | null }) {
  if (!severity) return <span className="text-[12.5px] text-ink-subtle">Not triaged</span>;
  const tone = { LOW: 'neutral', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger' }[severity] as
    'neutral' | 'info' | 'warning' | 'danger' | undefined;
  return <Badge tone={tone ?? 'neutral'}>{severity.charAt(0) + severity.slice(1).toLowerCase()}</Badge>;
}

export function HandlerStatusBadge({ status }: { status: string }) {
  const tone = {
    AVAILABLE: 'success', BALANCED: 'success', UNAVAILABLE: 'warning', HIGH_UTILIZATION: 'warning',
    AT_CAPACITY: 'warning', INACTIVE: 'neutral', OVER_CAPACITY: 'danger',
  }[status] as 'success' | 'warning' | 'neutral' | 'danger' | undefined;
  return <Badge tone={tone ?? 'neutral'} dot>{HANDLER_STATUS_LABEL[status] ?? status}</Badge>;
}

export function DecisionBadge({ status }: { status: string | null }) {
  if (!status) return null;
  const map: Record<string, ['success' | 'info' | 'warning' | 'neutral' | 'danger', string]> = {
    APPROVED: ['success', 'Approved'],
    OVERRIDDEN: ['info', 'Overridden'],
    MANUAL_REVIEW: ['warning', 'Manual review'],
    PENDING: ['neutral', 'Pending'],
    CANCELLED: ['neutral', 'Superseded'],
  };
  const [tone, label] = map[status] ?? ['neutral', status];
  return <Badge tone={tone}>{label}</Badge>;
}
