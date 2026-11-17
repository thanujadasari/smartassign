import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Lock, LogOut, Plug, RotateCcw, Save, ShieldCheck, SlidersHorizontal, UserRound } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input, Switch } from '@/components/ui/input';
import { Alert, Progress, Tabs } from '@/components/ui/misc';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { useToast } from '@/components/ui/toast';
import { PageHeader } from '@/components/common/PageHeader';
import { ErrorState, PageSkeleton } from '@/components/common/States';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { api, toApiError } from '@/lib/api';
import { CAN_EDIT, useAuth } from '@/lib/auth';
import { useAsync } from '@/lib/useAsync';
import { fmtDateTime, titleCase } from '@/lib/format';
import type { EligibilityRule, WeightsInput } from '@/lib/types';
import { cn } from '@/lib/utils';

type Section = 'weights' | 'rules' | 'integration' | 'account';

const FACTORS: { key: keyof WeightsInput; label: string; help: string }[] = [
  { key: 'expertise', label: 'Expertise match', help: 'Specialist in the claim line scores highest; multi-line handlers slightly lower' },
  { key: 'workload', label: 'Workload fit', help: 'Share of the handler’s capacity still free' },
  { key: 'experience', label: 'Relevant experience', help: 'Years of experience (20 years = full score)' },
  { key: 'successRate', label: 'Historical success rate', help: 'Share of the handler’s resolved claims closed within the target time (neutral for new handlers)' },
  { key: 'similarClaims', label: 'Similar claims handled', help: 'Number of comparable claims handled (100 = full score)' },
  { key: 'proximity', label: 'Location proximity', help: 'Distance between the handler’s base and the loss location' },
  { key: 'predictedFit', label: 'Predicted claim fit', help: 'Fit estimated from past assignment outcomes; redistributed when unavailable' },
];
const DEFAULTS: WeightsInput = { expertise: 10, workload: 20, experience: 10, successRate: 15, similarClaims: 10, proximity: 15, predictedFit: 20,
  fairnessThreshold: 0.85, fairnessPenalty: 0.15, usePredictedFit: true };

export default function SettingsPage() {
  const [params, setParams] = useSearchParams();
  const section = (params.get('tab') ?? 'weights') as Section;
  return (
    <>
      <PageHeader title="Settings" description="Configure how eligible handlers are ranked, review the mandatory eligibility rules and integration status." />
      <Tabs<Section> value={section} onChange={(v) => setParams({ tab: v }, { replace: true })} className="mb-6 flex-wrap" items={[
        { value: 'weights', label: <span className="inline-flex items-center gap-1.5"><SlidersHorizontal className="size-3.5" />Ranking weights</span> },
        { value: 'rules', label: <span className="inline-flex items-center gap-1.5"><ShieldCheck className="size-3.5" />Eligibility rules</span> },
        { value: 'integration', label: <span className="inline-flex items-center gap-1.5"><Plug className="size-3.5" />ClaimCenter integration</span> },
        { value: 'account', label: <span className="inline-flex items-center gap-1.5"><UserRound className="size-3.5" />Account</span> },
      ]} />
      {section === 'weights' && <WeightsSection />}
      {section === 'rules' && <RulesSection />}
      {section === 'integration' && <IntegrationSection />}
      {section === 'account' && <AccountSection />}
    </>
  );
}

function WeightsSection() {
  const { hasRole } = useAuth();
  const canEdit = hasRole(...CAN_EDIT);
  const { notify } = useToast();
  const current = useAsync(() => api.settings.weights(), []);
  const history = useAsync(() => api.settings.weightHistory(), []);
  const [w, setW] = useState<WeightsInput | null>(null);
  const [confirm, setConfirm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (current.data) {
      const { id: _i, total: _t, active: _a, updatedBy: _u, updatedAt: _d, ...rest } = current.data;
      setW(rest);
    }
  }, [current.data]);

  if (current.loading && !w) return <PageSkeleton />;
  if (current.error) return <ErrorState error={current.error} onRetry={current.reload} />;
  if (!w || !current.data) return null;

  const total = FACTORS.reduce((s, f) => s + (Number(w[f.key]) || 0), 0);
  const valid = Math.abs(total - 100) < 0.01 && FACTORS.every((f) => Number(w[f.key]) >= 0 && Number(w[f.key]) <= 100);
  const dirty = FACTORS.some((f) => Number(w[f.key]) !== Number(current.data![f.key])) || w.fairnessThreshold !== current.data.fairnessThreshold
    || w.fairnessPenalty !== current.data.fairnessPenalty || w.usePredictedFit !== current.data.usePredictedFit;

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      await api.settings.updateWeights(w);
      notify('success', 'Ranking weights saved', 'New recommendations will use these weights. The change is recorded in the audit log.');
      setConfirm(false);
      current.reload();
      history.reload();
    } catch (e) {
      setError(toApiError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="grid gap-6 xl:grid-cols-3">
      <Card className="xl:col-span-2">
        <CardHeader title="Scoring factor weights" description="How much each factor contributes to the 0–100 suitability score of eligible handlers. Weights must total exactly 100%." />
        <CardBody className="space-y-5">
          {!canEdit && <Alert tone="info">Your role can view but not change ranking weights.</Alert>}
          {FACTORS.map((f) => (
            <div key={f.key} className="grid items-center gap-3 sm:grid-cols-[220px_1fr_88px]">
              <div>
                <label htmlFor={`w-${f.key}`} className="text-[13px] font-medium text-ink">{f.label}</label>
                <p className="text-[11.5px] leading-snug text-ink-subtle">{f.help}</p>
              </div>
              <input type="range" min={0} max={50} step={1} value={Number(w[f.key])} disabled={!canEdit} aria-label={`${f.label} weight`}
                onChange={(e) => setW({ ...w, [f.key]: Number(e.target.value) })} className="w-full accent-navy-900" />
              <div className="relative">
                <Input id={`w-${f.key}`} type="number" min={0} max={100} step={1} value={Number(w[f.key])} disabled={!canEdit} className="pr-7 text-right tabular"
                  onChange={(e) => setW({ ...w, [f.key]: e.target.value === '' ? 0 : Number(e.target.value) })} />
                <span className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-[12px] text-ink-subtle">%</span>
              </div>
            </div>
          ))}
          <div className={cn('rounded-lg border p-4', valid ? 'border-success-border bg-success-soft' : 'border-danger-border bg-danger-soft')}>
            <div className="flex items-center justify-between text-[13px]">
              <span className="font-medium text-ink">Total</span>
              <span className={cn('text-[16px] font-semibold tabular', valid ? 'text-success' : 'text-danger')}>{total.toFixed(0)}%</span>
            </div>
            <Progress value={Math.min(100, total)} tone={valid ? 'success' : 'danger'} className="mt-2" label="Weight total" />
            {!valid && <p role="alert" className="mt-2 text-[12.5px] text-danger">{total > 100 ? `Reduce the weights by ${(total - 100).toFixed(0)}%` : `Add ${(100 - total).toFixed(0)}% to reach 100%`} before saving.</p>}
          </div>
        </CardBody>
      </Card>

      <div className="space-y-6">
        <Card>
          <CardHeader title="Workload fairness control" />
          <CardBody className="space-y-4">
            <div>
              <div className="flex justify-between text-[13px]"><label htmlFor="ft" className="font-medium text-ink">Utilisation threshold</label><span className="tabular">{Math.round(w.fairnessThreshold * 100)}%</span></div>
              <input id="ft" type="range" min={50} max={100} value={Math.round(w.fairnessThreshold * 100)} disabled={!canEdit} className="mt-2 w-full accent-navy-900"
                onChange={(e) => setW({ ...w, fairnessThreshold: Number(e.target.value) / 100 })} />
              <p className="text-[11.5px] text-ink-subtle">If a claim would take a handler above this share of capacity…</p>
            </div>
            <div>
              <div className="flex justify-between text-[13px]"><label htmlFor="fp" className="font-medium text-ink">Score reduction</label><span className="tabular">{Math.round(w.fairnessPenalty * 100)}%</span></div>
              <input id="fp" type="range" min={0} max={50} value={Math.round(w.fairnessPenalty * 100)} disabled={!canEdit} className="mt-2 w-full accent-navy-900"
                onChange={(e) => setW({ ...w, fairnessPenalty: Number(e.target.value) / 100 })} />
              <p className="text-[11.5px] text-ink-subtle">…their suitability score is reduced by this much.</p>
            </div>
            <div className="flex items-start justify-between gap-3 border-t border-border pt-4">
              <div>
                <p className="text-[13px] font-medium text-ink">Use predicted claim fit</p>
                <p className="text-[11.5px] text-ink-subtle">When off, its weight is shared across the other factors.</p>
              </div>
              <Switch label="Use predicted claim fit" checked={w.usePredictedFit} disabled={!canEdit} onChange={(v) => setW({ ...w, usePredictedFit: v })} />
            </div>
          </CardBody>
        </Card>
        {canEdit && (
          <Card>
            <CardBody className="flex flex-col gap-2">
              <Button onClick={() => { setError(null); setConfirm(true); }} disabled={!valid || !dirty}><Save /> Save weights</Button>
              <Button variant="secondary" onClick={() => setW(DEFAULTS)}><RotateCcw /> Reset to recommended defaults</Button>
              <p className="text-[11.5px] text-ink-subtle">Last changed by {current.data.updatedBy ?? 'system'} · {fmtDateTime(current.data.updatedAt)}</p>
            </CardBody>
          </Card>
        )}
        <Card>
          <CardHeader title="Change history" />
          {history.data && history.data.length > 0 ? (
            <ul className="max-h-64 divide-y divide-border overflow-y-auto">
              {history.data.map((hw) => (
                <li key={hw.id} className="flex items-center justify-between gap-2 px-5 py-2.5 text-[12.5px]">
                  <span>{fmtDateTime(hw.updatedAt)} · {hw.updatedBy ?? 'system'}</span>
                  {hw.active ? <Badge tone="success">Active</Badge> : <span className="text-ink-subtle">fit {hw.predictedFit}% · workload {hw.workload}%</span>}
                </li>
              ))}
            </ul>
          ) : <CardBody><p className="text-[12.5px] text-ink-muted">No changes yet.</p></CardBody>}
        </Card>
      </div>

      <ConfirmDialog open={confirm} onClose={() => setConfirm(false)} onConfirm={save} pending={saving} error={error} confirmLabel="Save weights"
        title="Apply new ranking weights?" description="All future recommendations will use these weights. Previous recommendations are not changed." />
    </div>
  );
}

function RulesSection() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const { data, error, loading, reload } = useAsync(() => api.settings.rules(), []);
  if (loading && !data) return <PageSkeleton />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <div className="space-y-4">
      <Alert tone="info" title="Hard eligibility comes first">
        Every handler must pass every enabled rule before they can be ranked or assigned — no score can override a failed rule.
        Core rules are locked on. {isAdmin ? 'Business rules can be adjusted below.' : 'Only administrators can change business rules.'}
      </Alert>
      {data?.map((r) => <RuleCard key={r.code} rule={r} editable={isAdmin && !r.locked} onSaved={reload} />)}
    </div>
  );
}

function RuleCard({ rule, editable, onSaved }: { rule: EligibilityRule; editable: boolean; onSaved: () => void }) {
  const { notify } = useToast();
  const [enabled, setEnabled] = useState(rule.enabled);
  const [p1, setP1] = useState(rule.param1 == null ? '' : String(rule.param1));
  const [p2, setP2] = useState(rule.param2 == null ? '' : String(rule.param2));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dirty = enabled !== rule.enabled || p1 !== (rule.param1 == null ? '' : String(rule.param1)) || p2 !== (rule.param2 == null ? '' : String(rule.param2));
  const description = rule.description.replace(/^ASSUMED BUSINESS RESTRICTION - /, '');
  const assumed = rule.description.startsWith('ASSUMED');

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      await api.settings.updateRule(rule.code, { enabled, param1: p1 === '' ? null : Number(p1), param2: p2 === '' ? null : Number(p2) });
      notify('success', `${rule.name} updated`, 'Applies to the next eligibility check.');
      onSaved();
    } catch (e) {
      setError(toApiError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardBody className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-[14px] font-semibold text-ink">{rule.name}</p>
            {rule.locked ? <Badge tone="navy"><Lock /> Core · always on</Badge> : <Badge tone="info">Business rule</Badge>}
            {assumed && <Badge tone="warning">Assumed policy — confirm with your organisation</Badge>}
            {!rule.locked && <Badge tone={rule.enabled ? 'success' : 'neutral'} dot>{rule.enabled ? 'Enabled' : 'Disabled'}</Badge>}
          </div>
          <p className="mt-1 text-[13px] text-ink-muted">{description.replace(/param_1/g, rule.param1Label?.toLowerCase() ?? 'the threshold').replace(/param_2/g, rule.param2Label?.toLowerCase() ?? 'the minimum')}</p>
          {(rule.param1Label || rule.param2Label) && (
            <div className="mt-3 flex flex-wrap gap-4">
              {rule.param1Label && (
                <label className="flex items-center gap-2 text-[12.5px] text-ink-muted">{rule.param1Label}
                  <Input type="number" step="any" value={p1} disabled={!editable} onChange={(e) => setP1(e.target.value)} className="w-24 tabular" />
                </label>
              )}
              {rule.param2Label && (
                <label className="flex items-center gap-2 text-[12.5px] text-ink-muted">{rule.param2Label}
                  <Input type="number" step="any" value={p2} disabled={!editable} onChange={(e) => setP2(e.target.value)} className="w-24 tabular" />
                </label>
              )}
            </div>
          )}
          {error && <Alert tone="danger" className="mt-3">{error}</Alert>}
          <p className="mt-2 text-[11.5px] text-ink-subtle">Updated {fmtDateTime(rule.updatedAt)}{rule.updatedBy ? ` by ${rule.updatedBy}` : ''}</p>
        </div>
        <div className="flex items-center gap-3">
          {!rule.locked && <Switch label={`Enable ${rule.name}`} checked={enabled} disabled={!editable} onChange={setEnabled} />}
          {editable && <Button size="sm" onClick={save} disabled={!dirty} loading={saving}>Save</Button>}
        </div>
      </CardBody>
    </Card>
  );
}

function IntegrationSection() {
  const { data, error, loading, reload } = useAsync(() => api.integration.claimCenter(), []);
  if (loading && !data) return <PageSkeleton />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (!data) return null;
  return (
    <div className="space-y-6">
      <Card>
        <CardBody className="flex items-start gap-4">
          <span className="flex size-10 items-center justify-center rounded-md bg-navy-50 text-navy-600"><Plug className="size-5" /></span>
          <div>
            <div className="flex items-center gap-2"><p className="text-[15px] font-semibold text-ink">Guidewire ClaimCenter</p><Badge tone="neutral" dot>{data.status.configured ? 'Connected' : 'Not connected'}</Badge></div>
            <p className="mt-1 text-[13px] text-ink-muted">{data.status.message}</p>
          </div>
        </CardBody>
      </Card>
      {[{ title: 'Claim field mapping', rows: data.claimFields }, { title: 'Handler field mapping', rows: data.handlerFields }].map((t) => (
        <Card key={t.title}>
          <CardHeader title={t.title} description="Indicative — confirm every field against your ClaimCenter data model and version" />
          <Table>
            <THead><TR><TH>SmartAssign field</TH><TH>ClaimCenter source</TH><TH>Direction</TH><TH>Notes</TH></TR></THead>
            <TBody>
              {t.rows.map((r) => (
                <TR key={r.smartAssignField}>
                  <TD className="font-medium">{r.smartAssignField}</TD>
                  <TD className="text-ink-muted">{r.claimCenterSource}</TD>
                  <TD><Badge tone={r.direction.startsWith('OUT') ? 'warning' : 'info'}>{r.direction === 'IN' ? 'Import' : 'Write-back (optional)'}</Badge></TD>
                  <TD className="text-ink-muted">{r.notes || '—'}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </Card>
      ))}
      <Card>
        <CardHeader title="Limitations" />
        <CardBody><ul className="list-disc space-y-1.5 pl-5 text-[13px] text-ink-muted">{data.limitations.map((l) => <li key={l}>{l}</li>)}</ul></CardBody>
      </Card>
    </div>
  );
}

function AccountSection() {
  const { user, logout } = useAuth();
  if (!user) return null;
  return (
    <Card className="max-w-xl">
      <CardHeader title="Your account" />
      <CardBody className="space-y-3 text-[13px]">
        {[['Name', user.fullName], ['Email', user.email], ['Phone', user.phone ?? '—'], ['Role', titleCase(user.role)], ['Last sign-in', fmtDateTime(user.lastLoginAt)]].map(([k, v]) => (
          <div key={k} className="flex justify-between gap-4"><span className="text-ink-muted">{k}</span><span className="font-medium text-ink">{v}</span></div>
        ))}
        <Button variant="secondary" onClick={() => logout()}><LogOut /> Sign out</Button>
      </CardBody>
    </Card>
  );
}
