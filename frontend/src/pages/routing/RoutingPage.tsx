import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowRight, CircleCheck, ClipboardList, FileSearch, Info, ListChecks, RotateCcw, ShieldAlert, Sparkles, UserCheck, Workflow } from 'lucide-react';
import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Alert } from '@/components/ui/misc';
import { useToast } from '@/components/ui/toast';
import { PageHeader } from '@/components/common/PageHeader';
import { EmptyState } from '@/components/common/States';
import { DecisionBadge } from '@/components/common/StatusBadges';
import { api, toApiError, type ApiError } from '@/lib/api';
import { CAN_EDIT, useAuth } from '@/lib/auth';
import { fmtDateTime } from '@/lib/format';
import type { Assignment, Claim, EligibilityResponse, RecommendationResponse } from '@/lib/types';
import { Stepper } from './Stepper';
import { ClaimQueue } from './ClaimQueue';
import { ClaimSummary } from './ClaimSummary';
import { EligibilityPanel, ExclusionBreakdown } from './EligibilityPanel';
import { RecommendationCard } from './RecommendationCard';
import { DecisionDialog, type Decision } from './DecisionDialogs';

export default function RoutingPage() {
  const [params, setParams] = useSearchParams();
  const { hasRole } = useAuth();
  const canDecide = hasRole(...CAN_EDIT);
  const { notify } = useToast();

  const [claim, setClaim] = useState<Claim | null>(null);
  const [eligibility, setEligibility] = useState<EligibilityResponse | null>(null);
  const [recs, setRecs] = useState<RecommendationResponse | null>(null);
  const [pendingAssignment, setPendingAssignment] = useState<Assignment | null>(null);
  const [result, setResult] = useState<Assignment | null>(null);
  const [busy, setBusy] = useState<'eligibility' | 'recommend' | 'decide' | null>(null);
  const [stepError, setStepError] = useState<ApiError | null>(null);
  const [selectedAlt, setSelectedAlt] = useState<number | null>(null);
  const [decision, setDecision] = useState<Decision | null>(null);
  const [decisionError, setDecisionError] = useState<string | null>(null);
  const [queueVersion, setQueueVersion] = useState(0);

  const reset = () => {
    setEligibility(null); setRecs(null); setPendingAssignment(null); setResult(null);
    setStepError(null); setSelectedAlt(null); setDecision(null);
  };

  // pre-select from ?claimId=
  useEffect(() => {
    const id = Number(params.get('claimId'));
    if (!id || claim?.id === id) return;
    api.claims.get(id).then((d) => { reset(); setClaim(d.claim); }).catch((e) => setStepError(toApiError(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params]);

  const select = (c: Claim) => {
    reset();
    setClaim(c);
    setParams({ claimId: String(c.id) }, { replace: true });
  };

  const runEligibility = async () => {
    if (!claim) return;
    setBusy('eligibility'); setStepError(null); setRecs(null); setPendingAssignment(null);
    try { setEligibility(await api.routing.eligibility(claim.id)); } catch (e) { setStepError(toApiError(e)); } finally { setBusy(null); }
  };

  const runRecommend = async () => {
    if (!claim) return;
    setBusy('recommend'); setStepError(null); setSelectedAlt(null); setPendingAssignment(null);
    try {
      const r = await api.routing.recommend(claim.id);
      setRecs(r);
      setClaim(r.claim);
      if (r.fallback) {
        setQueueVersion((v) => v + 1);
        notify('info', 'No eligible handler', r.fallback.newlyQueued ? 'The claim has been placed in the manual review queue.' : undefined);
      }
    } catch (e) { setStepError(toApiError(e)); } finally { setBusy(null); }
  };

  const expired = recs?.validUntil ? new Date(recs.validUntil).getTime() < Date.now() : false;
  const top = recs?.recommendations[0];
  const alt = recs?.recommendations.find((r) => r.handler.id === selectedAlt) ?? null;

  const confirmDecision = async (text: string) => {
    if (!decision || !claim || !recs) return;
    setBusy('decide'); setDecisionError(null);
    try {
      const pending = pendingAssignment ?? (await api.assignments.create(claim.id, recs.runId));
      setPendingAssignment(pending);
      const done = decision.kind === 'approve'
        ? await api.assignments.approve(pending.id, text || undefined)
        : decision.kind === 'override'
          ? await api.assignments.override(pending.id, decision.rec.handler.id, text)
          : await api.assignments.manualReview(pending.id, text);
      setResult(done);
      setDecision(null);
      api.claims.get(claim.id).then((d) => setClaim(d.claim)).catch(() => undefined);
      window.scrollTo({ top: 0, behavior: 'smooth' });
      setQueueVersion((v) => v + 1);
      notify('success', done.status === 'MANUAL_REVIEW' ? `Claim ${claim.claimNumber} sent for manual review`
        : `Claim ${claim.claimNumber} assigned to ${done.assignedHandler?.fullName}`, 'Decision and explanation recorded in the audit log.');
    } catch (e) {
      const err = toApiError(e);
      setDecisionError([err.message, ...err.details].join(' '));
    } finally { setBusy(null); }
  };

  const stage = result ? 3 : recs ? (recs.fallback ? 3 : 2) : eligibility ? 1 : claim ? 1 : 0;
  const completed = result || recs?.fallback ? 4 : recs ? 3 : eligibility ? 2 : claim ? 1 : 0;
  const routable = claim && (claim.status === 'UNASSIGNED' || claim.status === 'MANUAL_REVIEW');

  const scoringAlert = useMemo(() => {
    if (!recs || recs.fallback || !recs.scoringNote) return null;
    return <Alert tone={recs.scoringBasis === 'PROFILE_AND_PREDICTED_FIT' ? 'info' : 'warning'}>{recs.scoringNote}</Alert>;
  }, [recs]);

  return (
    <>
      <PageHeader title="SmartAssign Routing" eyebrow={<span className="text-[12px] font-semibold uppercase tracking-[0.14em] text-accent-600">Assignment recommendations</span>}
        description="Mandatory eligibility rules are applied first. Only eligible handlers are ranked, every recommendation is explained, and you make the final decision." />
      <Stepper current={stage} completed={completed} />

      <div className="mt-6 grid gap-6 xl:grid-cols-[340px_minmax(0,1fr)]">
        <ClaimQueue selectedId={claim?.id ?? null} onSelect={select} version={queueVersion} />

        <div className="min-w-0 space-y-6">
          {!claim ? (
            <Card><EmptyState icon={<FileSearch />} title="Select a claim to begin"
              description="Choose a claim from the queue. SmartAssign will check every handler against the mandatory rules, then rank the eligible handlers with an explanation for each." /></Card>
          ) : (
            <>
              <ClaimSummary claim={claim} />
              {stepError && <Alert tone="danger" title="This step could not be completed">{stepError.message}</Alert>}
              {!canDecide && <Alert tone="info">Your role can view routing results but cannot make assignment decisions.</Alert>}

              {!result && routable && (
                <Card>
                  <CardBody className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex items-start gap-3">
                      <ListChecks className="mt-0.5 size-5 text-navy-500" />
                      <div>
                        <p className="text-[14px] font-semibold text-ink">{recs ? 'Recommendations generated' : eligibility ? 'Eligibility checked' : 'Step 2 · Check hard eligibility'}</p>
                        <p className="text-[12.5px] text-ink-muted">
                          {recs ? `Generated ${fmtDateTime(recs.createdAt)}. Re-run to use the latest handler data.` :
                            eligibility ? `${eligibility.eligibleCount} eligible, ${eligibility.ineligibleCount} excluded. Next: rank the eligible handlers.` :
                              'Apply the mandatory rules (active, available, expertise, capacity and business restrictions) to every handler.'}
                        </p>
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <Button variant={eligibility ? 'secondary' : 'primary'} onClick={runEligibility} loading={busy === 'eligibility'} disabled={!canDecide || !!busy}>
                        {eligibility ? <><RotateCcw /> Re-check eligibility</> : <><ShieldAlert /> Run eligibility check</>}
                      </Button>
                      {eligibility && (
                        <Button variant={recs ? 'secondary' : 'primary'} onClick={runRecommend} loading={busy === 'recommend'} disabled={!canDecide || !!busy}>
                          {recs ? <><RotateCcw /> Regenerate</> : <><Sparkles /> Generate recommendations</>}
                        </Button>
                      )}
                    </div>
                  </CardBody>
                </Card>
              )}

              {!routable && !result && (
                <Alert tone="info" title={`Claim ${claim.claimNumber} ${claim.status === 'SUBMITTED' ? 'has not been triaged yet' : claim.status === 'ASSIGNED' ? 'is already assigned' : 'is resolved'}`}>
                  {claim.status === 'SUBMITTED' ? 'Confirm severity, complexity and fraud risk first.' : 'Only triaged claims that are ready to assign or in manual review can be routed.'}{' '}
                  <Link to={`/claims/${claim.id}`} className="font-medium underline">{claim.status === 'SUBMITTED' ? 'Triage the claim' : 'Open the claim record'}</Link>.
                </Alert>
              )}

              {result && <DecisionResult result={result} onNext={() => { reset(); setClaim(null); setParams({}, { replace: true }); }} />}

              {recs?.fallback && (
                <Card className="border-warning-border">
                  <CardHeader title="No eligible handler — safe fallback applied" icon={<ShieldAlert />}
                    description="Hard eligibility rules were not relaxed. No ranking was produced." />
                  <CardBody className="space-y-4">
                    <Alert tone="warning" title={recs.fallback.newlyQueued ? 'Claim sent to the manual review queue' : 'Claim remains in manual review'}>
                      {recs.fallback.reason}
                    </Alert>
                    <div>
                      <p className="mb-2 text-[12.5px] font-medium text-ink">Why handlers were excluded</p>
                      <ExclusionBreakdown items={recs.exclusionBreakdown} total={recs.ineligibleCount} />
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Link to={`/claims/${claim.id}`} className={buttonVariants({ variant: 'secondary' })}><ClipboardList /> View claim record</Link>
                      <Link to="/handlers?status=UNAVAILABLE" className={buttonVariants({ variant: 'secondary' })}><UserCheck /> Review handler availability</Link>
                    </div>
                  </CardBody>
                </Card>
              )}

              {recs && !recs.fallback && top && !result && (
                <Card>
                  <CardHeader title={`Ranked eligible handlers (${recs.recommendations.length})`} icon={<Sparkles />}
                    description="Ranked by an overall suitability score (0–100) built from the factors below. Select a card’s breakdown to see each factor’s contribution." />
                  <CardBody className="space-y-4">
                    {scoringAlert}
                    {expired && <Alert tone="warning" title="These recommendations have expired">Handler workloads may have changed. Regenerate before deciding.</Alert>}
                    <div className="space-y-3">
                      {recs.recommendations.slice(0, 10).map((r) => (
                        <RecommendationCard key={r.handler.id} rec={r} top={r.rank === 1} defaultOpen={r.rank === 1}
                          selected={selectedAlt === r.handler.id} disabled={!canDecide || expired}
                          onSelect={canDecide ? () => setSelectedAlt(selectedAlt === r.handler.id ? null : r.handler.id) : undefined} />
                      ))}
                    </div>
                    {recs.recommendations.length > 10 && (
                      <p className="text-[12px] text-ink-subtle">Showing the top 10 of {recs.recommendations.length} eligible handlers.</p>
                    )}
                  </CardBody>
                  {canDecide && (
                    <div className="sticky bottom-0 flex flex-wrap items-center justify-between gap-3 rounded-b-lg border-t border-border bg-surface/95 px-5 py-3.5 backdrop-blur">
                      <p className="text-[12.5px] text-ink-muted">
                        {alt ? <>Selected alternative: <strong className="text-ink">{alt.handler.fullName}</strong> (rank #{alt.rank})</> :
                          <>Step 4 · Approve <strong className="text-ink">{top.handler.fullName}</strong>, choose another eligible handler, or send for review.</>}
                      </p>
                      <div className="flex flex-wrap gap-2">
                        <Button variant="warning" onClick={() => { setDecisionError(null); setDecision({ kind: 'review' }); }} disabled={!!busy || expired}>
                          <ClipboardList /> Send for manual review
                        </Button>
                        {alt ? (
                          <Button onClick={() => { setDecisionError(null); setDecision({ kind: 'override', rec: alt, top }); }} disabled={!!busy || expired}>
                            <UserCheck /> Assign selected handler
                          </Button>
                        ) : (
                          <Button variant="success" onClick={() => { setDecisionError(null); setDecision({ kind: 'approve', rec: top }); }} disabled={!!busy || expired}>
                            <CircleCheck /> Approve recommendation
                          </Button>
                        )}
                      </div>
                    </div>
                  )}
                </Card>
              )}

              {eligibility && !result && !recs?.fallback && (
                <EligibilityPanel eligible={eligibility.eligible} ineligible={eligibility.ineligible}
                  breakdown={eligibility.exclusionBreakdown} rulesApplied={eligibility.rulesApplied} />
              )}

              {recs && !recs.fallback && Object.keys(recs.weightsApplied).length > 0 && !result && (
                <Card>
                  <CardHeader title="How the score is built" icon={<Info />} description="Weights applied in this run (configurable in Settings)" />
                  <CardBody className="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
                    {Object.entries(recs.weightsApplied).filter(([, w]) => w > 0).map(([label, w]) => (
                      <div key={label} className="rounded-md bg-canvas p-2.5">
                        <p className="text-[11.5px] text-ink-muted">{label}</p>
                        <p className="text-[15px] font-semibold text-ink tabular">{w.toFixed(1)}%</p>
                      </div>
                    ))}
                  </CardBody>
                </Card>
              )}
            </>
          )}
        </div>
      </div>

      <DecisionDialog decision={decision} onClose={() => setDecision(null)} onConfirm={confirmDecision}
        pending={busy === 'decide'} error={decisionError} claimNumber={claim?.claimNumber ?? ''} />
    </>
  );
}

function DecisionResult({ result, onNext }: { result: Assignment; onNext: () => void }) {
  const review = result.status === 'MANUAL_REVIEW';
  return (
    <Card className={review ? 'border-warning-border' : 'border-success-border'}>
      <CardBody className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <span className={`flex size-10 items-center justify-center rounded-full ${review ? 'bg-warning-soft text-warning' : 'bg-success-soft text-success'}`}>
            {review ? <ClipboardList className="size-5" /> : <CircleCheck className="size-5" />}
          </span>
          <div>
            <div className="flex items-center gap-2">
              <p className="text-[15px] font-semibold text-ink">
                {review ? 'Claim sent for manual review' : `Assigned to ${result.assignedHandler?.fullName} (${result.assignedHandler?.code})`}
              </p>
              <DecisionBadge status={result.status} />
            </div>
            <p className="mt-1 text-[12.5px] text-ink-muted">
              Decision recorded {fmtDateTime(result.decidedAt)} by {result.decidedBy}. Claim status, handler workload and the audit log have been updated.
            </p>
            {result.overrideReason && <p className="mt-2 text-[12.5px] text-ink"><strong>Override reason:</strong> {result.overrideReason}</p>}
            {result.reviewReason && <p className="mt-2 text-[12.5px] text-ink"><strong>Reason:</strong> {result.reviewReason}</p>}
          </div>
        </div>
        <div className="flex gap-2">
          <Link to={`/claims/${result.claimId}`} className={buttonVariants({ variant: 'secondary' })}>View claim</Link>
          <Button onClick={onNext}><Workflow /> Route next claim <ArrowRight /></Button>
        </div>
      </CardBody>
    </Card>
  );
}
