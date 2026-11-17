import axios, { AxiosError } from 'axios';
import type {
  ApiErrorBody, Assignment, AuditEntry, Claim, ClaimDetail,
  CustomerClaim, CustomerClaimDetail, CustomerClaimInput, CustomerSignupInput, DashboardSummary, EligibilityResponse,
  EligibilityRule, FairnessSummary, Handler, HandlerDetail, HandlerProfile, HandlerUpdateInput, IntegrationStatus,
  LoginResponse, Page, Priority, RankingWeights, RecommendationResponse, ReferenceOptions, StaffSignupInput, TriageInput,
  TriageSuggestion, User, WeightsInput,
} from './types';

export const TOKEN_KEY = 'smartassign.token';

export const http = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL ?? ''}/api`,
  timeout: 60_000,
  headers: { Accept: 'application/json' },
});

function readToken(): string | null {
  try {
    return window.sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

http.interceptors.request.use((config) => {
  const token = readToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let onUnauthorized: (() => void) | null = null;
export const setUnauthorizedHandler = (fn: (() => void) | null) => {
  onUnauthorized = fn;
};

http.interceptors.response.use(
  (r) => r,
  (error: AxiosError) => {
    if (error.response?.status === 401 && !String(error.config?.url ?? '').includes('/auth/login')) {
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

/** Normalised error for the UI: a sentence, optional field errors and detail lines. */
export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;
  details: string[];

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, details: string[] = []) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.details = details;
  }
}

export function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  if (axios.isAxiosError(e)) {
    const body = e.response?.data as Partial<ApiErrorBody> | undefined;
    if (!e.response) {
      return new ApiError(0, 'The SmartAssign service could not be reached. Check your connection and try again.');
    }
    const fields: Record<string, string> = {};
    for (const f of body?.fieldErrors ?? []) fields[f.field] = f.message;
    const message =
      body?.message ??
      (e.response.status === 403 ? 'You do not have permission to perform this action.' : 'Something went wrong. Please try again.');
    return new ApiError(e.response.status, message, fields, body?.details ?? []);
  }
  return new ApiError(-1, e instanceof Error ? e.message : 'Unexpected error');
}

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data);

type Params = Record<string, string | number | boolean | undefined | null>;
const clean = (p: Params) => Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== ''));

async function download(url: string, params: Params, fallbackName: string) {
  const res = await http.get(url, { params: clean(params), responseType: 'blob' });
  const disposition = String(res.headers['content-disposition'] ?? '');
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const href = URL.createObjectURL(res.data as Blob);
  const a = document.createElement('a');
  a.href = href;
  a.download = match?.[1] ?? fallbackName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(href);
}

export const api = {
  auth: {
    login: (email: string, password: string) => data(http.post<LoginResponse>('/auth/login', { email, password })),
    me: () => data(http.get<User>('/auth/me')),
    logout: () => data(http.post<void>('/auth/logout', {})),
    registrationOptions: () => data(http.get<{ staffSignupEnabled: boolean }>('/auth/registration-options')),
    registerCustomer: (body: CustomerSignupInput) => data(http.post<User>('/auth/register/customer', body)),
    registerStaff: (body: StaffSignupInput) => data(http.post<User>('/auth/register/staff', body)),
  },
  customer: {
    submit: (body: CustomerClaimInput) => data(http.post<CustomerClaimDetail>('/customer/claims', body)),
    list: (page = 0, size = 20) => data(http.get<Page<CustomerClaim>>('/customer/claims', { params: { page, size } })),
    get: (id: number) => data(http.get<CustomerClaimDetail>(`/customer/claims/${id}`)),
  },
  handlerPortal: {
    me: () => data(http.get<HandlerProfile>('/handler-portal/me')),
    setAvailability: (available: boolean, reason?: string) =>
      data(http.put<HandlerProfile>('/handler-portal/availability', { available, reason })),
    claims: (view: 'open' | 'resolved', page = 0, size = 20) =>
      data(http.get<Page<Claim>>('/handler-portal/claims', { params: { view, page, size } })),
    claim: (id: number) => data(http.get<ClaimDetail>(`/handler-portal/claims/${id}`)),
    resolve: (id: number, note: string) => data(http.post<ClaimDetail>(`/handler-portal/claims/${id}/resolve`, { note })),
  },
  reference: {
    options: () => data(http.get<ReferenceOptions>('/reference/options')),
  },
  dashboard: {
    summary: () => data(http.get<DashboardSummary>('/dashboard/summary')),
  },
  claims: {
    list: (p: Params) => data(http.get<Page<Claim>>('/claims', { params: clean(p) })),
    get: (id: number) => data(http.get<ClaimDetail>(`/claims/${id}`)),
    activity: (id: number) => data(http.get<AuditEntry[]>(`/claims/${id}/activity`)),
    triageSuggestion: (id: number) => data(http.get<TriageSuggestion>(`/claims/${id}/triage-suggestion`)),
    triage: (id: number, body: TriageInput) => data(http.post<ClaimDetail>(`/claims/${id}/triage`, body)),
    changePriority: (id: number, priority: Priority, reason: string) =>
      data(http.patch<ClaimDetail>(`/claims/${id}/priority`, { priority, reason })),
  },
  handlers: {
    list: (p: Params) => data(http.get<Page<Handler>>('/handlers', { params: clean(p) })),
    get: (id: number) => data(http.get<HandlerDetail>(`/handlers/${id}`)),
    update: (id: number, body: HandlerUpdateInput) => data(http.put<Handler>(`/handlers/${id}`, body)),
    setActive: (id: number, active: boolean, reason?: string) =>
      data(http.patch<Handler>(`/handlers/${id}/status`, { active, reason })),
  },
  routing: {
    eligibility: (claimId: number) => data(http.post<EligibilityResponse>(`/routing/${claimId}/eligibility`)),
    recommend: (claimId: number) => data(http.post<RecommendationResponse>(`/routing/${claimId}/recommendations`)),
  },
  assignments: {
    create: (claimId: number, runId: number) => data(http.post<Assignment>('/assignments', { claimId, runId })),
    approve: (id: number, note?: string) => data(http.post<Assignment>(`/assignments/${id}/approve`, { note })),
    override: (id: number, handlerId: number, reason: string) =>
      data(http.post<Assignment>(`/assignments/${id}/override`, { handlerId, reason })),
    manualReview: (id: number, reason: string) =>
      data(http.post<Assignment>(`/assignments/${id}/manual-review`, { reason })),
    list: (p: Params) => data(http.get<Page<Assignment>>('/assignments', { params: clean(p) })),
  },
  audit: {
    list: (p: Params) => data(http.get<Page<AuditEntry>>('/audit', { params: clean(p) })),
    exportCsv: (p: Params) => download('/audit/export', p, 'smartassign-audit.csv'),
  },
  fairness: {
    summary: (windowDays: number) => data(http.get<FairnessSummary>('/fairness/summary', { params: { windowDays } })),
  },
  settings: {
    weights: () => data(http.get<RankingWeights>('/settings/ranking-weights')),
    weightHistory: () => data(http.get<RankingWeights[]>('/settings/ranking-weights/history')),
    updateWeights: (body: WeightsInput) => data(http.put<RankingWeights>('/settings/ranking-weights', body)),
    rules: () => data(http.get<EligibilityRule[]>('/settings/eligibility-rules')),
    updateRule: (code: string, body: { enabled: boolean; param1?: number | null; param2?: number | null }) =>
      data(http.put<EligibilityRule>(`/settings/eligibility-rules/${code}`, body)),
  },
  integration: {
    claimCenter: () => data(http.get<IntegrationStatus>('/integration/claimcenter/status')),
  },
};
