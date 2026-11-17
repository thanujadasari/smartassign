import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { CircleCheck, Info, X, CircleX } from 'lucide-react';
import { cn } from '@/lib/utils';

type Tone = 'success' | 'error' | 'info';
interface Toast { id: number; tone: Tone; title: string; description?: string }

const ToastContext = createContext<{ notify: (tone: Tone, title: string, description?: string) => void } | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const notify = useCallback((tone: Tone, title: string, description?: string) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t.slice(-3), { id, tone, title, description }]);
    setTimeout(() => dismiss(id), tone === 'error' ? 8000 : 4500);
  }, [dismiss]);

  return (
    <ToastContext.Provider value={{ notify }}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[360px] max-w-[calc(100vw-2rem)] flex-col gap-2">
        {toasts.map((t) => {
          const Icon = t.tone === 'success' ? CircleCheck : t.tone === 'error' ? CircleX : Info;
          return (
            <div key={t.id} role={t.tone === 'error' ? 'alert' : 'status'}
              className="pointer-events-auto flex gap-3 rounded-lg border border-border bg-surface p-3.5 shadow-pop animate-slide-in">
              <Icon className={cn('mt-0.5 size-4 shrink-0',
                t.tone === 'success' ? 'text-success' : t.tone === 'error' ? 'text-danger' : 'text-info')} aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="text-[13px] font-semibold text-ink">{t.title}</p>
                {t.description && <p className="mt-0.5 text-[12.5px] text-ink-muted">{t.description}</p>}
              </div>
              <button type="button" onClick={() => dismiss(t.id)} aria-label="Dismiss notification"
                className="h-fit cursor-pointer rounded p-0.5 text-ink-subtle hover:text-ink">
                <X className="size-3.5" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
}
