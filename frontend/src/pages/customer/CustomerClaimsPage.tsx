import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { FilePlus2, Inbox } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { buttonVariants } from '@/components/ui/button';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { PageHeader } from '@/components/common/PageHeader';
import { ClaimStatusBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, TableSkeleton } from '@/components/common/States';
import { Pagination } from '@/components/common/Pagination';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useAsync } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtCurrency, fmtDate } from '@/lib/format';

export default function CustomerClaimsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const claims = useAsync(() => api.customer.list(page, size), [page, size]);

  return (
    <>
      <PageHeader title={`Hello, ${user?.fullName.split(' ')[0] ?? ''}`} description="Your registered claims and their progress."
        actions={<Link to="/customer/new" className={buttonVariants()}><FilePlus2 /> Report a claim</Link>} />
      <Card>
        {claims.error ? <ErrorState error={claims.error} onRetry={claims.reload} /> :
          claims.loading && !claims.data ? <TableSkeleton cols={6} /> :
          claims.data!.totalElements === 0 ? (
            <EmptyState icon={<Inbox />} title="You haven't reported any claims yet"
              description="Report a claim and our team will review it and assign a claim handler."
              action={<Link to="/customer/new" className={buttonVariants({ size: 'sm' })}><FilePlus2 /> Report a claim</Link>} />
          ) : (
            <>
              <Table>
                <THead><TR>
                  <TH>Claim</TH><TH>Type</TH><TH>Incident date</TH><TH className="text-right">Estimated loss</TH><TH>Handler</TH><TH>Status</TH>
                </TR></THead>
                <TBody>
                  {claims.data!.content.map((c) => (
                    <TR key={c.id} interactive onClick={() => navigate(`/customer/claims/${c.id}`)}>
                      <TD>
                        <Link to={`/customer/claims/${c.id}`} className="font-medium text-ink hover:underline" onClick={(e) => e.stopPropagation()}>{c.claimNumber}</Link>
                        <p className="text-[12px] text-ink-subtle">Policy {c.policyNumber}</p>
                      </TD>
                      <TD>{CLAIM_TYPE_LABEL[c.claimType]}</TD>
                      <TD>{fmtDate(c.incidentDate)}</TD>
                      <TD className="text-right tabular">{fmtCurrency(c.estimatedLoss)}</TD>
                      <TD>{c.handler ? c.handler.fullName : <span className="text-ink-subtle">Not yet assigned</span>}</TD>
                      <TD><ClaimStatusBadge status={c.status} label={c.statusLabel} /></TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
              <Pagination page={page} size={size} total={claims.data!.totalElements} totalPages={claims.data!.totalPages}
                onPage={setPage} onSize={(s) => { setSize(s); setPage(0); }} />
            </>
          )}
      </Card>
    </>
  );
}
