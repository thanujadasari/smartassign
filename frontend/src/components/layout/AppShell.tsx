import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Menu } from 'lucide-react';
import { useAuth } from '@/lib/auth';
import { PORTAL_LABEL } from './nav';
import { cn } from '@/lib/utils';
import { Breadcrumbs } from './Breadcrumbs';
import { Notifications } from './Notifications';
import { Sidebar } from './Sidebar';
import { UserMenu } from './UserMenu';

const KEY = 'smartassign.sidebar.collapsed';

export function AppShell() {
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return window.localStorage.getItem(KEY) === '1';
    } catch {
      return false;
    }
  });
  const [mobileOpen, setMobileOpen] = useState(false);
  const { pathname } = useLocation();
  const { user } = useAuth();

  useEffect(() => setMobileOpen(false), [pathname]);
  useEffect(() => {
    try {
      window.localStorage.setItem(KEY, collapsed ? '1' : '0');
    } catch {
      /* preference only */
    }
  }, [collapsed]);

  return (
    <div className="min-h-full">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>
      <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((c) => !c)} mobileOpen={mobileOpen} onNavigate={() => setMobileOpen(false)} />
      {mobileOpen && <div className="fixed inset-0 z-30 bg-navy-950/40 lg:hidden" onClick={() => setMobileOpen(false)} aria-hidden />}

      <div className={cn('flex min-h-full flex-col transition-[padding] duration-200', collapsed ? 'lg:pl-[72px]' : 'lg:pl-[248px]')}>
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-surface/90 px-4 backdrop-blur sm:px-6">
          <button type="button" onClick={() => setMobileOpen(true)} aria-label="Open navigation"
            className="flex size-9 cursor-pointer items-center justify-center rounded-md text-ink-muted hover:bg-navy-50 lg:hidden">
            <Menu className="size-5" />
          </button>
          <div className="min-w-0 flex-1"><Breadcrumbs /></div>
          {user && (
            <span className="hidden items-center rounded-full border border-border bg-canvas px-2.5 py-1 text-[11.5px] font-medium text-ink-muted md:inline-flex">
              {PORTAL_LABEL[user.role]}
            </span>
          )}
          {user?.role === 'SUPERVISOR' && <Notifications />}
          <div className="h-6 w-px bg-border" aria-hidden />
          <UserMenu />
        </header>
        <main id="main" className="mx-auto w-full max-w-[1440px] flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
