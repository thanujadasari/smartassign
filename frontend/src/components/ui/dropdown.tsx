import { useEffect, useRef, useState, type ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** Small menu/popover: toggles on click, closes on outside click and Escape, returns focus to the trigger. */
export function Popover({ trigger, children, align = 'right', className, label }: {
  trigger: (props: { open: boolean; toggle: () => void }) => ReactNode;
  children: (close: () => void) => ReactNode; align?: 'left' | 'right'; className?: string; label?: string;
}) {
  const [open, setOpen] = useState(false);
  const wrap = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (wrap.current && !wrap.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        wrap.current?.querySelector<HTMLElement>('button')?.focus();
      }
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);
  return (
    <div ref={wrap} className="relative">
      {trigger({ open, toggle: () => setOpen((o) => !o) })}
      {open && (
        <div role="menu" aria-label={label}
          className={cn('absolute z-40 mt-2 min-w-56 rounded-lg border border-border bg-surface p-1 shadow-pop animate-scale-in',
            align === 'right' ? 'right-0' : 'left-0', className)}>
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  );
}

export function MenuItem({ onClick, icon, children, tone = 'default' }:
  { onClick: () => void; icon?: ReactNode; children: ReactNode; tone?: 'default' | 'danger' }) {
  return (
    <button type="button" role="menuitem" onClick={onClick}
      className={cn('flex w-full cursor-pointer items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-[13px] [&_svg]:size-4',
        tone === 'danger' ? 'text-danger hover:bg-danger-soft' : 'text-ink hover:bg-navy-50')}>
      {icon && <span className="text-ink-subtle">{icon}</span>}
      {children}
    </button>
  );
}
