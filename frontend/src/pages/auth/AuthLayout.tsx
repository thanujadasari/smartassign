import type { ReactNode } from 'react';

/** The plain shield mark used on the sign-in and sign-up pages. */
function BrandMark() {
  return (
    <svg viewBox="0 0 32 32" className="size-[30px]" aria-hidden>
      <path d="M16 2.5 4.5 6.8v8.4c0 7.2 4.9 12.6 11.5 14.3 6.6-1.7 11.5-7.1 11.5-14.3V6.8L16 2.5Z" fill="#0f1f3a" />
      <path d="m10.6 16.1 3.6 3.6 7.3-7.4" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * Shared layout for sign in, customer sign-up and staff sign-up:
 * illustration panel on the left, the form in a card on the right (stacked on phones).
 */
export function AuthLayout({ title, subtitle, children, footer, wide }:
  { title: string; subtitle: ReactNode; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
  return (
    <div className="grid min-h-full bg-white lg:grid-cols-2">
      <section className="flex flex-col bg-accent-50 px-4 py-5 sm:px-8 lg:sticky lg:top-0 lg:h-screen lg:px-12 lg:py-10">
        <div className="flex items-center gap-2.5">
          <BrandMark />
          <span className="text-[21px] font-bold tracking-tight text-ink">SmartAssign</span>
        </div>
        <div className="flex flex-1 items-center justify-center py-4 lg:py-0">
          <img src="/illustrations/protect.svg" alt="" className="h-auto w-full max-w-[300px] lg:max-w-[560px]" />
        </div>
      </section>

      <section className="flex items-start justify-center bg-canvas px-4 py-6 sm:px-6 lg:items-center lg:py-12">
        <div className={`w-full rounded-2xl border border-navy-100 bg-white px-5 py-7 shadow-[0_1px_2px_rgba(15,31,58,0.04),0_12px_32px_rgba(15,31,58,0.08)] sm:px-10 sm:py-11 ${wide ? 'max-w-[560px]' : 'max-w-[440px]'}`}>
          <h1 className="text-[28px] font-semibold tracking-tight text-ink">{title}</h1>
          <div className="mt-3 h-1 w-11 rounded-full bg-accent-600" aria-hidden />
          <p className="mt-3.5 text-[15px] text-ink-muted">{subtitle}</p>
          <div className="mt-6 [&_input:not([type=checkbox])]:h-[46px] [&_select]:h-[46px] [&_button[type=submit]]:h-12">{children}</div>
          {footer && <div className="mt-6 text-[14px] leading-7 text-ink-muted">{footer}</div>}
        </div>
      </section>
    </div>
  );
}
