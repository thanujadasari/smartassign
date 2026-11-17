import { Link, useLocation } from 'react-router-dom';
import { ChevronRight, House } from 'lucide-react';
import { useAuth, HOME_FOR } from '@/lib/auth';
import { SEGMENT_LABEL } from './nav';

export function Breadcrumbs() {
  const { pathname } = useLocation();
  const { user } = useAuth();
  const home = user ? HOME_FOR[user.role] : '/';
  // the portal root ("/customer", "/handler") is shown as the home crumb, not repeated
  const all = pathname.split('/').filter(Boolean).filter((p, i) => !(i === 0 && '/' + p === home));
  const prefix = home === '/' ? '' : home;
  // portal claim pages live at <portal>/claims/:id - there is no "<portal>/claims" list page to link to
  const parts = prefix ? all.filter((p) => p !== 'claims') : all;
  const crumbs = parts.map((p, i) => {
    const to = prefix + '/' + all.slice(0, all.indexOf(p) + 1).join('/');
    const parent = parts[i - 1];
    const label = SEGMENT_LABEL[p]
      ?? (parent === 'claims' || /^\d+$/.test(p) && parent !== 'handlers' ? 'Claim Details' : parent === 'handlers' ? 'Handler Details' : p);
    return { to, label };
  });
  return (
    <nav aria-label="Breadcrumb" className="min-w-0">
      <ol className="flex items-center gap-1.5 text-[13px] text-ink-muted">
        <li>
          <Link to={home} className="flex items-center gap-1 hover:text-ink">
            <House className="size-3.5" aria-hidden />
            <span className={crumbs.length ? 'sr-only sm:not-sr-only' : ''}>
              {user?.role === 'CUSTOMER' ? 'My Claims' : user?.role === 'HANDLER' ? 'Assigned Claims' : 'Overview'}
            </span>
          </Link>
        </li>
        {crumbs.map((c, i) => (
          <li key={c.to} className="flex min-w-0 items-center gap-1.5">
            <ChevronRight className="size-3.5 shrink-0 text-ink-subtle" aria-hidden />
            {i === crumbs.length - 1 ? (
              <span aria-current="page" className="truncate font-medium text-ink">{c.label}</span>
            ) : (
              <Link to={c.to} className="truncate hover:text-ink">{c.label}</Link>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
