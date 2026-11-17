import { forwardRef, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes, type LabelHTMLAttributes, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

const field =
  'w-full rounded-md border bg-surface px-3 text-sm text-ink shadow-xs placeholder:text-ink-subtle transition-colors ' +
  'focus:outline-none focus:ring-2 focus:ring-accent-500/25 focus:border-accent-500 disabled:bg-navy-50 disabled:text-ink-muted';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(
  ({ className, invalid, ...props }, ref) => (
    <input ref={ref} aria-invalid={invalid || undefined}
      className={cn(field, 'h-9', invalid ? 'border-danger' : 'border-border-strong', className)} {...props} />
  ),
);
Input.displayName = 'Input';

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }>(
  ({ className, invalid, ...props }, ref) => (
    <textarea ref={ref} aria-invalid={invalid || undefined}
      className={cn(field, 'min-h-20 py-2', invalid ? 'border-danger' : 'border-border-strong', className)} {...props} />
  ),
);
Textarea.displayName = 'Textarea';

/** Native select styled to match - fully keyboard and screen-reader accessible. */
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }>(
  ({ className, invalid, children, ...props }, ref) => (
    <div className={cn('relative', className)}>
      <select ref={ref} aria-invalid={invalid || undefined}
        className={cn(field, 'h-9 appearance-none pr-8', invalid ? 'border-danger' : 'border-border-strong')} {...props}>
        {children}
      </select>
      <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 size-4 -translate-y-1/2 text-ink-subtle" aria-hidden />
    </div>
  ),
);
Select.displayName = 'Select';

export function Label({ className, ...props }: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn('text-[12.5px] font-medium text-ink', className)} {...props} />;
}

export function Field({ label, htmlFor, error, hint, required, children, className }:
  { label: ReactNode; htmlFor: string; error?: string; hint?: ReactNode; required?: boolean; children: ReactNode; className?: string }) {
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <Label htmlFor={htmlFor}>
        {label}
        {required && <span className="ml-0.5 text-danger" aria-hidden>*</span>}
      </Label>
      {children}
      {error ? (
        <p id={`${htmlFor}-error`} role="alert" className="text-[12px] text-danger">{error}</p>
      ) : hint ? (
        <p className="text-[12px] text-ink-subtle">{hint}</p>
      ) : null}
    </div>
  );
}

export function Switch({ checked, onChange, disabled, id, label }:
  { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; id?: string; label?: string }) {
  return (
    <button id={id} type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn('relative inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-50', checked ? 'bg-navy-900' : 'bg-border-strong')}>
      <span className={cn('inline-block size-4 rounded-full bg-white shadow transition-transform',
        checked ? 'translate-x-[18px]' : 'translate-x-0.5')} />
    </button>
  );
}

export function Checkbox({ checked, onChange, id, label, disabled }:
  { checked: boolean; onChange: (v: boolean) => void; id: string; label: ReactNode; disabled?: boolean }) {
  return (
    <label htmlFor={id} className="inline-flex cursor-pointer items-center gap-2 text-sm text-ink">
      <input id={id} type="checkbox" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)}
        className="size-4 rounded border-border-strong accent-navy-900" />
      {label}
    </label>
  );
}
