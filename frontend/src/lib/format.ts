const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const num = new Intl.NumberFormat('en-IN');

export const fmtCurrency = (v: number | null | undefined) => (v == null ? '—' : inr.format(v));
export const fmtNumber = (v: number | null | undefined) => (v == null ? '—' : num.format(v));
export const fmtPercent = (v: number | null | undefined, digits = 0) =>
  v == null || Number.isNaN(v) ? '—' : `${(v * 100).toFixed(digits)}%`;
export const fmtScore = (v: number | null | undefined) => (v == null ? '—' : v.toFixed(1));

export function fmtDate(v: string | null | undefined) {
  if (!v) return '—';
  const d = new Date(v);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function fmtDateTime(v: string | null | undefined) {
  if (!v) return '—';
  const d = new Date(v);
  return d.toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function fmtRelative(v: string | null | undefined) {
  if (!v) return '—';
  const diff = (Date.now() - new Date(v).getTime()) / 1000;
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`;
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} d ago`;
  return fmtDate(v);
}

/** "WORKERS_COMPENSATION" -> "Workers Compensation" */
export function titleCase(v: string | null | undefined) {
  if (!v) return '—';
  return v
    .toLowerCase()
    .split('_')
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(' ');
}

export const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('');
