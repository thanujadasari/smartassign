import type { HTMLAttributes, ReactNode, TdHTMLAttributes, ThHTMLAttributes } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { cn } from '@/lib/utils';

export function Table({ className, ...props }: HTMLAttributes<HTMLTableElement>) {
  return (
    <div className="relative w-full overflow-x-auto">
      <table className={cn('w-full border-collapse text-[13px]', className)} {...props} />
    </div>
  );
}

export function THead(props: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className="border-b border-border bg-canvas/70" {...props} />;
}

export function TBody(props: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className="divide-y divide-border" {...props} />;
}

export function TR({ className, interactive, ...props }: HTMLAttributes<HTMLTableRowElement> & { interactive?: boolean }) {
  return <tr className={cn('transition-colors', interactive && 'cursor-pointer hover:bg-navy-50/60', className)} {...props} />;
}

export function TH({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th className={cn('h-10 px-4 text-left align-middle text-[11.5px] font-semibold uppercase tracking-wide text-ink-muted whitespace-nowrap', className)} {...props} />;
}

export function TD({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-4 py-3 align-middle text-ink', className)} {...props} />;
}

/** Header cell with sort control; sort state lives in the page. */
export function SortTH({ field, sort, direction, onSort, children, className }: {
  field: string; sort: string; direction: 'asc' | 'desc'; onSort: (f: string) => void; children: ReactNode; className?: string;
}) {
  const active = sort === field;
  const Icon = !active ? ArrowUpDown : direction === 'asc' ? ArrowUp : ArrowDown;
  return (
    <TH className={className} aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" onClick={() => onSort(field)}
        className={cn('inline-flex cursor-pointer items-center gap-1 uppercase hover:text-ink', active && 'text-ink')}>
        {children}
        <Icon className={cn('size-3', active ? 'opacity-100' : 'opacity-40')} aria-hidden />
      </button>
    </TH>
  );
}
