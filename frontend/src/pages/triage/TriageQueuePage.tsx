import { Link, useNavigate } from 'react-router-dom';
import { ClipboardCheck } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { PageHeader } from '@/components/common/PageHeader';
import { EmptyState, ErrorState, TableSkeleton } from '@/components/common/States';
import { PriorityBadge } from '@/components/common/StatusBadges';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtCurrency, fmtDate, fmtRelative, titleCase } from '@/lib/format';

/** New claims registered by customers: most urgent suggested priority first, then oldest first. */
export default function TriageQueuePage() {
  const navigate = useNavigate();
  const res = useAsync(() => api.claims.list({ status: 'SUBMITTED', sort: 'priority', direction: 'asc', size: 100 }), []);
  return (
    <>
      <PageHeader title="Triage Queue" description="Claims registered by customers that need a severity, complexity and fraud-risk decision before they can be routed. Most urgent first (suggested priority), then oldest." />
      <Card>
        {res.error ? <ErrorState error={res.error} onRetry={res.reload} /> : !res.data ? <TableSkeleton cols={7} /> :
          res.data.content.length === 0 ? (
            <EmptyState icon={<ClipboardCheck />} title="Nothing to triage" description="New customer claims will appear here." />
          ) : (
            <Table>
              <THead><TR>
                <TH>Claim</TH><TH>Suggested priority</TH><TH>Customer</TH><TH>Line / incident</TH><TH>Incident date</TH><TH>Location</TH>
                <TH className="text-right">Est. loss</TH><TH>Waiting</TH>
              </TR></THead>
              <TBody>
                {res.data.content.map((c) => (
                  <TR key={c.id} interactive onClick={() => navigate(`/claims/${c.id}`)}>
                    <TD><Link to={`/claims/${c.id}`} onClick={(e) => e.stopPropagation()} className="font-medium text-accent-600 hover:underline">{c.claimNumber}</Link></TD>
                    <TD className="whitespace-nowrap"><PriorityBadge priority={c.priority.suggestedPriority} /></TD>
                    <TD className="whitespace-nowrap">{c.customer.fullName}</TD>
                    <TD>
                      <p className="whitespace-nowrap">{CLAIM_TYPE_LABEL[c.claimType]}</p>
                      <p className="whitespace-nowrap text-[12px] text-ink-subtle">{titleCase(c.incidentType)}{c.injuryInvolved ? ' · injury' : ''}{c.thirdPartyInvolved ? ' · third party' : ''}{c.ongoingRisk ? ' · ongoing risk' : ''}</p>
                    </TD>
                    <TD className="whitespace-nowrap">{fmtDate(c.incidentDate)}</TD>
                    <TD>{c.location}</TD>
                    <TD className="text-right tabular">{fmtCurrency(c.estimatedLoss)}</TD>
                    <TD className="whitespace-nowrap text-ink-muted">{fmtRelative(c.createdAt)}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          )}
      </Card>
    </>
  );
}
