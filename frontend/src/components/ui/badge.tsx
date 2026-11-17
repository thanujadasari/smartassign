import type { HTMLAttributes } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

export const badgeVariants = cva(
  'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11.5px] font-medium leading-4 whitespace-nowrap [&_svg]:size-3',
  {
    variants: {
      tone: {
        neutral: 'bg-navy-50 text-navy-700 border-navy-100',
        info: 'bg-info-soft text-info border-info-border',
        success: 'bg-success-soft text-success border-success-border',
        warning: 'bg-warning-soft text-warning border-warning-border',
        danger: 'bg-danger-soft text-danger border-danger-border',
        navy: 'bg-navy-900 text-white border-navy-900',
        outline: 'bg-transparent text-ink-muted border-border-strong',
      },
    },
    defaultVariants: { tone: 'neutral' },
  },
);

export function Badge({ className, tone, dot, children, ...props }:
  HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants> & { dot?: boolean }) {
  return (
    <span className={cn(badgeVariants({ tone }), className)} {...props}>
      {dot && <span className="size-1.5 rounded-full bg-current" aria-hidden />}
      {children}
    </span>
  );
}
