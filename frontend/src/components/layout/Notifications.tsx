import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TriangleAlert, Bell, ClipboardList, ServerCrash } from 'lucide-react';
import { Popover } from '@/components/ui/dropdown';
import { api } from '@/lib/api';
import { cn } from '@/lib/utils';

interface Note { id: string; tone: 'warning' | 'danger' | 'info'; title: string; body: string; to: string }

/** Notifications are derived from live backend data - nothing is invented on the client. */
export function Notifications() {
  const navigate = useNavigate();
  const [notes, setNotes] = useState<Note[]>([]);
  const [seen, setSeen] = useState(false);

  const load = useCallback(async () => {
    const out: Note[] = [];
    const [dash, fair] = await Promise.allSettled([api.dashboard.summary(), api.fairness.summary(30)]);
    if (dash.status === 'fulfilled') {
      const d = dash.value;
      if (d.kpis.awaitingTriage > 0) {
        out.push({ id: 'tr', tone: 'info', title: `${d.kpis.awaitingTriage} new claim(s) awaiting triage`,
          body: 'Registered by customers - confirm severity, complexity and fraud risk.', to: '/triage' });
      }
      if (d.kpis.unavailableHandlers > 0) {
        out.push({ id: 'ua', tone: 'info', title: `${d.kpis.unavailableHandlers} handler(s) unavailable`,
          body: 'They will not receive new claims until they mark themselves available.', to: '/handlers?status=UNAVAILABLE' });
      }
      if (d.kpis.manualReviewClaims > 0) {
        out.push({ id: 'mr', tone: 'warning', title: `${d.kpis.manualReviewClaims} claim(s) awaiting manual review`,
          body: 'No eligible handler was found, or a supervisor requested review.', to: '/claims?status=MANUAL_REVIEW' });
      }
      if (d.system.suitabilityEngine === 'OFFLINE') {
        out.push({ id: 'ml', tone: 'info', title: 'Predicted claim fit is temporarily unavailable',
          body: 'Recommendations use handler profile factors only until it is back.', to: '/settings' });
      }
    }
    if (fair.status === 'fulfilled') {
      const w = fair.value.report.warnings;
      const critical = w.filter((x) => x.severity === 'CRITICAL').length;
      const warn = w.filter((x) => x.severity === 'WARNING').length;
      if (critical) out.push({ id: 'fc', tone: 'danger', title: `${critical} handler(s) over capacity`, body: 'Review workload in Fairness Monitoring.', to: '/fairness' });
      if (warn) out.push({ id: 'fw', tone: 'warning', title: `${warn} workload indicator(s) to review`, body: 'High utilisation or uneven distribution.', to: '/fairness' });
    }
    setNotes(out);
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(load, 60_000);
    return () => clearInterval(t);
  }, [load]);

  const count = seen ? 0 : notes.length;
  return (
    <Popover label="Notifications" className="w-[340px] p-0" trigger={({ open, toggle }) => (
      <button type="button" onClick={() => { toggle(); setSeen(true); }} aria-expanded={open} aria-haspopup="menu"
        aria-label={`Notifications${count ? ` (${count} new)` : ''}`}
        className="relative flex size-9 cursor-pointer items-center justify-center rounded-md text-ink-muted hover:bg-navy-50 hover:text-ink">
        <Bell className="size-[18px]" />
        {count > 0 && (
          <span className="absolute right-1 top-1 flex min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold leading-4 text-white">
            {count}
          </span>
        )}
      </button>
    )}>
      {(close) => (
        <div>
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <p className="text-[13px] font-semibold text-ink">Notifications</p>
            <span className="text-[11.5px] text-ink-subtle">{notes.length} active</span>
          </div>
          {notes.length === 0 ? (
            <p className="px-4 py-8 text-center text-[13px] text-ink-muted">You're all caught up.</p>
          ) : (
            <ul className="max-h-80 divide-y divide-border overflow-y-auto">
              {notes.map((n) => {
                const Icon = n.id === 'mr' ? ClipboardList : n.id === 'ml' ? ServerCrash : TriangleAlert;
                return (
                  <li key={n.id}>
                    <button type="button" role="menuitem" onClick={() => { close(); navigate(n.to); }}
                      className="flex w-full cursor-pointer gap-3 px-4 py-3 text-left hover:bg-navy-50">
                      <span className={cn('mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md',
                        n.tone === 'danger' ? 'bg-danger-soft text-danger' : n.tone === 'warning' ? 'bg-warning-soft text-warning' : 'bg-info-soft text-info')}>
                        <Icon className="size-3.5" />
                      </span>
                      <span>
                        <span className="block text-[13px] font-medium text-ink">{n.title}</span>
                        <span className="block text-[12px] text-ink-muted">{n.body}</span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </Popover>
  );
}
