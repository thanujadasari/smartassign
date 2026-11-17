import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '@/lib/auth';
import { ToastProvider } from '@/components/ui/toast';
import { TOKEN_KEY } from '@/lib/api';

/** Renders inside router + toast + auth providers; signed in unless `signedIn` is false. */
export function renderApp(ui: ReactElement, { route = '/', signedIn = true } = {}) {
  if (signedIn) window.sessionStorage.setItem(TOKEN_KEY, 'test-token');
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ToastProvider><AuthProvider>{ui}</AuthProvider></ToastProvider>
    </MemoryRouter>,
  );
}
