import type { ReactNode } from 'react';
import { CalendarClock, FileCheck2, Mail, MapPin, Phone, ShieldAlert } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState } from '@/components/common/States';
import { CLAIM_STATUS_LABEL, CLAIM_TYPE_LABEL } from '@/lib/labels';
import { fmtCurrency, fmtDate, fmtDateTime, fmtPercent, initials, titleCase } from '@/lib/format';
import type { StatusEvent } from '@/lib/types';

export function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 text-[13px]">
      <dt className="shrink-0 text-ink-muted">{label}</dt>
      <dd className="min-w-0 text-right font-medium break-words text-ink">{value}</dd>
    </div>
  );
}

export function Metric({ label, value, tone, hint }: { label: string; value: string; tone?: string; hint?: ReactNode }) {
  return (
    <div>
      <p className="text-[12px] text-ink-muted">{label}</p>
      <p className={`mt-1 text-[18px] font-semibold tabular ${tone ?? 'text-ink'}`}>{value}</p>
      {hint && <p className="mt-0.5 text-[11.5px] text-ink-subtle">{hint}</p>}
    </div>
  );
}

interface CustomerFacts {
  claimType: string; policyType: string; policyNumber: string; incidentType: string; incidentDate: string;
  location: string; estimatedLoss: number; injuryInvolved: boolean; thirdPartyInvolved: boolean; ongoingRisk: boolean; description: string;
  createdAt: string;
}

/** The details the customer entered - identical wording in all three portals. */
export function ClaimFacts({ c, triage }: {
  c: CustomerFacts;
  triage?: { severity: string | null; complexity: string | null; fraudRiskScore: number | null };
}) {
  return (
    <>
      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader title="Policy" icon={<FileCheck2 />} />
          <CardBody><dl className="space-y-3">
            <Row label="Line of business" value={CLAIM_TYPE_LABEL[c.claimType] ?? c.claimType} />
            <Row label="Policy type" value={titleCase(c.policyType)} />
            <Row label="Policy number" value={c.policyNumber} />
            <Row label="Registered" value={fmtDateTime(c.createdAt)} />
          </dl></CardBody>
        </Card>
        <Card>
          <CardHeader title="Incident" icon={<MapPin />} />
          <CardBody><dl className="space-y-3">
            <Row label="Incident type" value={titleCase(c.incidentType)} />
            <Row label="Date of incident" value={fmtDate(c.incidentDate)} />
            <Row label="Location" value={c.location} />
            <Row label="Bodily injury" value={c.injuryInvolved ? 'Yes' : 'No'} />
            <Row label="Third party involved" value={c.thirdPartyInvolved ? 'Yes' : 'No'} />
            <Row label="Ongoing risk or damage" value={c.ongoingRisk ? <span className="text-danger">Yes</span> : 'No'} />
          </dl></CardBody>
        </Card>
      </div>
      <Card>
        <CardHeader title="Loss details" icon={<ShieldAlert />} />
        <CardBody className="grid gap-4 sm:grid-cols-4">
          <Metric label="Estimated loss" value={fmtCurrency(c.estimatedLoss)} hint="As stated by the customer" />
          {triage && (
            <>
              <Metric label="Severity" value={triage.severity ? titleCase(triage.severity) : 'Not triaged'} />
              <Metric label="Complexity" value={triage.complexity ? titleCase(triage.complexity) : 'Not triaged'} />
              <Metric label="Fraud risk" value={triage.fraudRiskScore == null ? 'Not triaged' : fmtPercent(triage.fraudRiskScore)}
                tone={triage.fraudRiskScore == null ? 'text-ink-subtle' : triage.fraudRiskScore >= 0.7 ? 'text-danger' : triage.fraudRiskScore >= 0.4 ? 'text-warning' : undefined} />
            </>
          )}
          <div className="sm:col-span-4">
            <p className="mb-1 text-[12px] text-ink-muted">What happened</p>
            <p className="whitespace-pre-line rounded-md bg-canvas p-3 text-[13px] leading-relaxed text-ink">{c.description}</p>
          </div>
        </CardBody>
      </Card>
    </>
  );
}

export function PersonCard({ name, caption, email, phone, to }: {
  name: string; caption: ReactNode; email?: string | null; phone?: string | null; to?: ReactNode;
}) {
  return (
    <div className="rounded-lg border border-border p-3">
      <div className="flex items-center gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-navy-900 text-[13px] font-semibold text-white">
          {initials(name)}
        </span>
        <span className="min-w-0">
          <span className="block truncate text-[13.5px] font-medium text-ink">{name}</span>
          <span className="block text-[12px] text-ink-muted">{caption}</span>
        </span>
        {to && <span className="ml-auto">{to}</span>}
      </div>
      {(email || phone) && (
        <div className="mt-2.5 space-y-1 border-t border-border pt-2.5 text-[12.5px] text-ink-muted">
          {email && <p className="flex items-center gap-2 break-all"><Mail className="size-3.5 shrink-0" /> <a href={`mailto:${email}`} className="hover:text-ink">{email}</a></p>}
          {phone && <p className="flex items-center gap-2"><Phone className="size-3.5 shrink-0" /> <a href={`tel:${phone}`} className="hover:text-ink">{phone}</a></p>}
        </div>
      )}
    </div>
  );
}

/** Status timeline stored in claim_status_history. */
export function Timeline({ events, title = 'Claim timeline', labels }: { events: StatusEvent[]; title?: string; labels?: Record<string, string> }) {
  return (
    <Card>
      <CardHeader title={title} icon={<CalendarClock />} />
      {events.length === 0 ? <EmptyState title="No history yet" /> : (
        <ol className="space-y-4 px-5 py-4">
          {events.map((e, i) => (
            <li key={i} className="relative pl-5">
              {i < events.length - 1 && <span className="absolute left-[3px] top-3 h-[calc(100%+8px)] w-px bg-border" aria-hidden />}
              <span className="absolute left-0 top-1.5 size-[7px] rounded-full bg-navy-500" aria-hidden />
              <p className="text-[13px] font-medium text-ink">
                {e.fromStatus === e.toStatus ? 'Updated' : labels?.[e.toStatus] ?? CLAIM_STATUS_LABEL[e.toStatus] ?? titleCase(e.toStatus)}
              </p>
              {e.note && <p className="mt-0.5 text-[12.5px] text-ink-muted">{e.note}</p>}
              <p className="mt-0.5 text-[12px] text-ink-subtle">{fmtDateTime(e.at)} · {e.actorName}</p>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
