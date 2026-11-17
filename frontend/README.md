# SmartAssign — supervisor web application

React 18 · TypeScript · Vite · Tailwind CSS v4 · Recharts · Lucide icons · React Router · Axios.

## Run
```bash
cd frontend
npm install
npm run dev            # http://localhost:5173 — /api is proxied to http://localhost:8080 (set VITE_BACKEND_URL to change)
npm run build          # production bundle in dist/
npm run typecheck      # TypeScript check
npm test               # Vitest + Testing Library
```
In Docker Compose the app is served by nginx at http://localhost:3000 and `/api` is proxied to the backend, so the
browser only ever talks to one origin. The ML service is internal to the backend and is never called from the browser.

## Structure
```
src/
  lib/            api.ts (typed client for every backend endpoint), types.ts (mirrors the backend DTOs), auth.tsx,
                  useAsync.ts, options.ts (reference data), format.ts, labels.ts (business wording for codes)
  components/ui/  design-system primitives: button, card, badge, inputs, dialog, menu, table, tabs, toast…
  components/     layout (sidebar, top bar, breadcrumbs, notifications, profile menu), common (KPI cards, states, score visuals)
  pages/          Overview dashboard, Claims (list/detail/form), Handlers (directory/detail/form), Routing workflow,
                  Assignment history & audit, Fairness monitoring, Settings, Login
  test/           Vitest suites
```

## Notes
* **UI components** follow the shadcn/ui pattern (source copied into `components/ui`, `class-variance-authority`
  variants, `tailwind-merge`) but are implemented without the Radix packages to keep dependencies small. Dialogs trap
  focus, close on Escape and restore focus; menus close on outside click/Escape; selects are native and fully accessible.
* **Notifications** are derived from live data (manual-review queue, fairness warnings, suitability-service status).
* **No model details are shown**: the ML output appears only as the factor "Predicted claim fit"; model names, metrics and
  versions are never rendered. The "Synthetic Dataset — Experimental Environment" label is shown in the header, the
  sidebar and on the dashboard.
* The session token is kept in `sessionStorage` (cleared when the tab closes); a 401 response signs the user out.
