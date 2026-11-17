import { Link } from 'react-router-dom';
import { ExternalLink } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { ClaimStatusBadge, PriorityBadge, SeverityBadge } from '@/components/common/StatusBadges';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtCurrency, fmtPercent, titleCase } from '@/lib/format';
import type { Claim } from '@/lib/types';

export function ClaimSummary({ claim }: { claim: Claim }) {
  const items: [string, string][] = [
    ['Line of business', CLAIM_TYPE_LABEL[claim.claimType]],
    ['Policy', titleCase(claim.policyType)],
    ['Incident', titleCase(claim.incidentType)],
    ['Location', claim.location],
    ['Estimated loss', fmtCurrency(claim.estimatedLoss)],
    ['Complexity', titleCase(claim.complexity)],
    ['Fraud risk', fmtPercent(claim.fraudRiskScore)],
    ['Registered', claim.claimAgeDays === 0 ? 'Today' : `${claim.claimAgeDays} day(s) ago`],
    ['Customer', claim.customer.fullName],
    ['Triaged by', claim.triage?.triagedBy?.fullName ?? '—'],
  ];
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <h2 className="text-[17px] font-semibold text-ink">Claim {claim.claimNumber}</h2>
          <ClaimStatusBadge status={claim.status} />
          <PriorityBadge priority={claim.priority.priority} />
          <SeverityBadge severity={claim.severity} />
          {claim.injuryInvolved && <span className="text-[12px] text-ink-muted">· injury involved</span>}
          {claim.thirdPartyInvolved && <span className="text-[12px] text-ink-muted">· third party</span>}
        </div>
        <Link to={`/claims/${claim.id}`} className="inline-flex items-center gap-1 text-[12.5px] text-accent-600 hover:underline">
          Claim record <ExternalLink className="size-3.5" />
        </Link>
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
        {items.map(([k, v]) => (
          <div key={k}>
            <dt className="text-[11.5px] uppercase tracking-wide text-ink-subtle">{k}</dt>
            <dd className="mt-0.5 text-[13.5px] font-medium text-ink">{v}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}
