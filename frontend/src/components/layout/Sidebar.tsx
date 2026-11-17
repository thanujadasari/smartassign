import { NavLink } from 'react-router-dom';
import { ChevronsLeft, ChevronsRight } from 'lucide-react';
import { Logo } from '@/components/common/Logo';
import { cn } from '@/lib/utils';
import { useAuth } from '@/lib/auth';
import { NAV_BY_ROLE, PORTAL_LABEL } from './nav';

export function Sidebar({ collapsed, onToggle, mobileOpen, onNavigate }:
  { collapsed: boolean; onToggle: () => void; mobileOpen: boolean; onNavigate: () => void }) {
  const { user } = useAuth();
  const sections = user ? NAV_BY_ROLE[user.role] : [];
  return (
    <aside
      className={cn(
        'fixed inset-y-0 left-0 z-40 flex flex-col bg-navy-950 text-navy-200 transition-[width,transform] duration-200',
        collapsed ? 'lg:w-[72px]' : 'lg:w-[248px]',
        'w-[248px] lg:translate-x-0',
        mobileOpen ? 'translate-x-0' : '-translate-x-full',
      )}
      aria-label="Main navigation"
    >
      <div className={cn('flex h-16 items-center border-b border-white/8 px-4', collapsed && 'lg:justify-center lg:px-0')}>
        <Logo collapsed={collapsed} inverted />
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4">
        {sections.map((section, si) => (
          <div key={section.title} className={cn(si > 0 && 'mt-6')}>
            {!collapsed && <p className="mb-2 px-3 text-[10.5px] font-semibold uppercase tracking-[0.14em] text-navy-400">{section.title}</p>}
            <ul className="space-y-0.5">
              {section.items.map((item) => {
                const Icon = item.icon;
                return (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      end={item.end}
                      onClick={onNavigate}
                      title={collapsed ? item.label : undefined}
                      className={({ isActive }) =>
                        cn(
                          'group relative flex items-center gap-3 rounded-md px-3 py-2 text-[13.5px] font-medium transition-colors',
                          collapsed && 'lg:justify-center lg:px-0',
                          isActive ? 'bg-white/10 text-white' : 'text-navy-300 hover:bg-white/5 hover:text-white',
                        )
                      }
                    >
                      {({ isActive }) => (
                        <>
                          {isActive && <span className="absolute left-0 top-1.5 bottom-1.5 w-[3px] rounded-r bg-accent-500" aria-hidden />}
                          <Icon className={cn('size-[18px] shrink-0', isActive ? 'text-accent-200' : 'text-navy-400 group-hover:text-navy-200')} />
                          <span className={cn(collapsed && 'lg:sr-only')}>{item.label}</span>
                        </>
                      )}
                    </NavLink>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      <div className="border-t border-white/8 p-3">
        {!collapsed && user && (
          <div className="mb-3 rounded-md bg-white/5 px-3 py-2.5">
            <p className="text-[11.5px] font-semibold text-accent-200">{PORTAL_LABEL[user.role]}</p>
            <p className="mt-0.5 truncate text-[11px] leading-snug text-navy-400">{user.email}</p>
          </div>
        )}
        <button
          type="button"
          onClick={onToggle}
          className="hidden w-full cursor-pointer items-center justify-center gap-2 rounded-md px-3 py-2 text-[12.5px] text-navy-400 hover:bg-white/5 hover:text-white lg:flex"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <ChevronsRight className="size-4" /> : <><ChevronsLeft className="size-4" /> Collapse</>}
        </button>
      </div>
    </aside>
  );
}
