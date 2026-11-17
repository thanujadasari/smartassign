import { cn } from '@/lib/utils';

export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={cn('size-8', className)} aria-hidden>
      <rect width="32" height="32" rx="7" fill="#0f1f3a" />
      <path d="M16 6l8 3v6c0 5.2-3.4 9.3-8 11-4.6-1.7-8-5.8-8-11V9z" fill="none" stroke="#7fb3ff" strokeWidth="2" />
      <path d="M12 16.5l3 3 5.5-6" fill="none" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function Logo({ collapsed, inverted }: { collapsed?: boolean; inverted?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <LogoMark className={inverted ? 'ring-1 ring-white/15 rounded-[7px]' : undefined} />
      {!collapsed && (
        <div className="leading-tight">
          <div className={cn('text-[15px] font-semibold tracking-tight', inverted ? 'text-white' : 'text-ink')}>
            Smart<span className={inverted ? 'text-accent-200' : 'text-accent-600'}>Assign</span>
          </div>
          <div className={cn('text-[10.5px] font-medium uppercase tracking-[0.12em]', inverted ? 'text-navy-300' : 'text-ink-subtle')}>
            Claims Routing
          </div>
        </div>
      )}
    </div>
  );
}
