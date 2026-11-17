import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, CalendarClock, ClipboardCheck, ShieldCheck, UserRound, Workflow } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Button, buttonVariants } from '@/components/ui/button';
import { Alert } from '@/components/ui/misc';
import { useToast } from '@/components/ui/toast';
import { ClaimPriority, ClaimStatusBadge, DecisionBadge, SeverityBadge } from '@/components/common/StatusBadges';
import { EmptyState, ErrorState, PageSkeleton } from '@/components/common/States';
import { ClaimFacts, PersonCard, Row, Timeline } from '@/components/claims/ClaimParts';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { CLAIM_TYPE_LABEL, EVENT_LABEL } from '@/lib/labels';
import { fmtDateTime, fmtPercent, fmtScore, titleCase } from '@/lib/format';
import { TriagePanel } from '../triage/TriagePanel';
import { PriorityCard } from './PriorityCard';

export default function ClaimDetailPage() {
  const id = Number(useParams().id);
  const { notify } = useToast();
  const res = useAsync(() => api.claims.get(id), [id]);
  const decisions = useAsync(() => api.assignments.list({ claimId: id, size: 50 }), [id]);
  const activity = useAsync(() => api.claims.activity(id), [id]);
  const [retriage, setRetriage] = useState(false);

  if (res.loading && !res.data) return <PageSkeleton />;
  if (res.error) return <ErrorState error={res.error} onRetry={res.reload} />;
  const { claim: c, timeline } = res.data!;
  const routable = c.status === 'UNASSIGNED' || c.status === 'MANUAL_REVIEW';
  const showTriage = c.status === 'SUBMITTED' || (retriage && routable);
  const t = c.triage;
  const decided = (decisions.data?.content ?? []).filter((d) => d.status !== 'CANCELLED');

  return (
    <>
      <Link to="/claims" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-ink-muted hover:text-ink">
        <ArrowLeft className="size-4" /> Back to claims
      </Link>

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-[22px] font-semibold tracking-tight text-ink">Claim {c.claimNumber}</h1>
            <ClaimStatusBadge status={c.status} />
            <ClaimPriority p={c.priority} />
            <SeverityBadge severity={c.severity} />
          </div>
          <p className="mt-1 text-[13.5px] text-ink-muted">
            {CLAIM_TYPE_LABEL[c.claimType]} claim · {titleCase(c.incidentType)} in {c.location} · registered by {c.customer.fullName} {c.claimAgeDays === 0 ? 'today' : `${c.claimAgeDays} day(s) ago`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {routable && !retriage && <Button variant="secondary" onClick={() => setRetriage(true)}><ClipboardCheck /> Re-triage</Button>}
          {routable && <Link to={`/routing?claimId=${c.id}`} className={buttonVariants()}><Workflow /> Route this claim</Link>}
        </div>
      </div>

      {showTriage && (
        <div className="mb-6">
          <TriagePanel claim={c} onCancel={c.status === 'SUBMITTED' ? undefined : () => setRetriage(false)}
            onDone={(d) => {
              res.setData(d);
              setRetriage(false);
              activity.reload();
              notify('success', `Claim ${d.claim.claimNumber} triaged`, 'It is ready to be routed to a handler.');
            }} />
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="min-w-0 space-y-6 xl:col-span-2">
          <ClaimFacts c={c} triage={c} />

          {t && (
            <Card>
              <CardHeader title="Triage decision" icon={<ClipboardCheck />}
                description={`By ${t.triagedBy?.fullName ?? '—'} · ${fmtDateTime(t.triagedAt)}`} />
              <CardBody className="space-y-3">
                <dl className="grid gap-3 sm:grid-cols-3">
                  <Row label="Severity" value={<>{titleCase(c.severity)} <span className="text-ink-subtle">(recommended {titleCase(t.suggestedSeverity)})</span></>} />
                  <Row label="Complexity" value={<>{titleCase(c.complexity)} <span className="text-ink-subtle">(recommended {titleCase(t.suggestedComplexity)})</span></>} />
                  <Row label="Fraud risk" value={<>{fmtPercent(c.fraudRiskScore)} <span className="text-ink-subtle">(recommended {fmtPercent(t.suggestedFraudRisk)})</span></>} />
                </dl>
                {t.note && <p className="rounded-md bg-canvas p-3 text-[13px] text-ink">{t.note}</p>}
              </CardBody>
            </Card>
          )}

          <Card>
            <CardHeader title="Assignment decisions" description="Every routing outcome for this claim, newest first" />
            {decisions.error ? <ErrorState error={decisions.error} onRetry={decisions.reload} compact /> :
              decided.length === 0 ? (
                <EmptyState title="No decisions yet"
                  description={c.status === 'SUBMITTED' ? 'Triage the claim first, then route it.' : routable ? 'Route this claim to receive explained handler recommendations.' : undefined}
                  action={routable ? <Link to={`/routing?claimId=${c.id}`} className={buttonVariants({ size: 'sm' })}><Workflow /> Route this claim</Link> : undefined} />
              ) : (
                <ul className="divide-y divide-border">
                  {decided.map((d) => (
                    <li key={d.id} className="px-5 py-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <DecisionBadge status={d.status} />
                          <span className="text-[13px] font-medium text-ink">
                            {d.assignedHandler ? `Assigned to ${d.assignedHandler.fullName} (${d.assignedHandler.code})` :
                              d.decisionType === 'FALLBACK_MANUAL_REVIEW' ? 'No eligible handler — sent to manual review' : 'Sent to manual review'}
                          </span>
                        </div>
                        <span className="text-[12px] text-ink-subtle">{fmtDateTime(d.decidedAt ?? d.createdAt)} · {d.decidedBy ?? d.createdBy}</span>
                      </div>
                      {d.recommendedHandler && (
                        <p className="mt-1.5 text-[12.5px] text-ink-muted">
                          Recommended: {d.recommendedHandler.fullName} ({d.recommendedHandler.code}) · suitability {fmtScore(d.recommendationScore)}
                        </p>
                      )}
                      {d.overrideReason && <p className="mt-1.5 text-[12.5px] text-ink"><span className="font-medium">Override reason:</span> {d.overrideReason}</p>}
                      {d.reviewReason && <p className="mt-1.5 text-[12.5px] text-ink"><span className="font-medium">Review reason:</span> {d.reviewReason}</p>}
                      {d.fallbackReason && <p className="mt-1.5 text-[12.5px] text-ink-muted">{d.fallbackReason}</p>}
                      {d.explanation && !d.fallbackReason && <p className="mt-1.5 text-[12.5px] italic text-ink-muted">“{d.explanation}”</p>}
                    </li>
                  ))}
                </ul>
              )}
          </Card>
        </div>

        <div className="min-w-0 space-y-6">
          <PriorityCard claim={c} onChanged={(d) => { res.setData(d); activity.reload(); }} />
          <Card>
            <CardHeader title="Customer" icon={<UserRound />} />
            <CardBody>
              <PersonCard name={c.customer.fullName} caption="Claimant" email={c.customer.email} phone={c.contactPhone ?? c.customer.phone} />
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Assignment" icon={<ShieldCheck />} />
            <CardBody>
              {c.assignedHandler ? (
                <div className="space-y-3">
                  <PersonCard name={c.assignedHandler.fullName} caption={`Handler ${c.assignedHandler.code}`}
                    email={c.assignedHandler.email} phone={c.assignedHandler.phone}
                    to={<Link to={`/handlers/${c.assignedHandler.id}`} className="text-[12.5px] font-medium text-accent-600 hover:underline">Profile</Link>} />
                  <dl className="space-y-2">
                    <Row label="Assigned by" value={c.assignedBy?.fullName ?? '—'} />
                    <Row label="Assigned" value={fmtDateTime(c.assignedAt)} />
                    {c.resolvedAt && <Row label="Resolved" value={`${fmtDateTime(c.resolvedAt)} (${c.resolutionDays} days)`} />}
                  </dl>
                  {c.resolutionNote && <Alert tone="success" title="Resolution note">{c.resolutionNote}</Alert>}
                </div>
              ) : c.status === 'MANUAL_REVIEW' ? (
                <p className="text-[13px] text-ink-muted">This claim is in the manual review queue. Route it again once a suitable handler is eligible.</p>
              ) : c.status === 'SUBMITTED' ? (
                <p className="text-[13px] text-ink-muted">Triage the claim before routing it to a handler.</p>
              ) : (
                <p className="text-[13px] text-ink-muted">No handler assigned yet.</p>
              )}
            </CardBody>
          </Card>

          <Timeline events={timeline} />

          <Card>
            <CardHeader title="Audit events" icon={<CalendarClock />} />
            {!activity.data || activity.data.length === 0 ? <EmptyState title="No activity recorded" /> : (
              <ol className="space-y-3 px-5 py-4">
                {activity.data.slice(0, 15).map((a) => (
                  <li key={a.id}>
                    <p className="text-[13px] font-medium text-ink">{EVENT_LABEL[a.eventType] ?? titleCase(a.eventType)}</p>
                    <p className="text-[12px] text-ink-muted">{fmtDateTime(a.createdAt)} · {a.actorUsername}</p>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}
