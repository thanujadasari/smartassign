import { vi } from 'vitest';

/** One shared mock of the API object; each test sets the responses it needs. */
export const mockApi = {
  auth: { login: vi.fn(), me: vi.fn(), logout: vi.fn().mockResolvedValue(undefined), registrationOptions: vi.fn().mockResolvedValue({ staffSignupEnabled: true }),
    registerCustomer: vi.fn(), registerStaff: vi.fn() },
  customer: { submit: vi.fn(), list: vi.fn(), get: vi.fn() },
  handlerPortal: { me: vi.fn(), setAvailability: vi.fn(), claims: vi.fn(), claim: vi.fn(), resolve: vi.fn() },
  reference: { options: vi.fn().mockResolvedValue({ claimTypes: [{ value: 'AUTO', label: 'Auto' }, { value: 'LIABILITY', label: 'Liability' }],
    policyTypes: [{ value: 'AUTO', label: 'Auto' }, { value: 'GENERAL_LIABILITY', label: 'General Liability' }], severities: [{ value: 'MEDIUM', label: 'Medium' }],
    complexities: [{ value: 'MEDIUM', label: 'Medium' }], incidentTypes: [{ value: 'COLLISION', label: 'Collision' }],
    expertise: [{ value: 'AUTO', label: 'Auto' }], claimStatuses: [], cities: ['Pune', 'Mumbai'],
    policyTypesByClaimType: { AUTO: ['AUTO'], LIABILITY: ['GENERAL_LIABILITY'] }, incidentTypesByClaimType: { AUTO: ['COLLISION'], LIABILITY: ['ACCIDENT'] } }) },
  dashboard: { summary: vi.fn() },
  claims: { list: vi.fn(), get: vi.fn(), activity: vi.fn().mockResolvedValue([]), triageSuggestion: vi.fn(), triage: vi.fn() },
  handlers: { list: vi.fn(), get: vi.fn(), update: vi.fn(), setActive: vi.fn() },
  routing: { eligibility: vi.fn(), recommend: vi.fn() },
  assignments: { create: vi.fn(), approve: vi.fn(), override: vi.fn(), manualReview: vi.fn(), list: vi.fn() },
  audit: { list: vi.fn(), exportCsv: vi.fn() },
  fairness: { summary: vi.fn() },
  settings: { weights: vi.fn(), weightHistory: vi.fn(), updateWeights: vi.fn(), rules: vi.fn(), updateRule: vi.fn() },
  integration: { claimCenter: vi.fn() },
};

export const emptyPage = { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 };
