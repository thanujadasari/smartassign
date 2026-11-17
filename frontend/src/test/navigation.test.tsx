import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { emptyPage, mockApi } from './mockApi';
import { customer, handlerUser, supervisor } from './fixtures';

vi.mock('@/lib/api', async (orig) => ({ ...(await orig<typeof import('@/lib/api')>()), api: mockApi }));
import App from '@/App';
import { renderApp } from './render';
import { ApiError } from '@/lib/api';

const dashboard = {
  kpis: { totalClaims: 3, awaitingTriage: 1, readyToAssign: 1, assignedClaims: 1, manualReviewClaims: 0, resolvedClaims: 0, availableHandlers: 2,
    activeHandlers: 2, unavailableHandlers: 0, eligibleAssignmentRate: null, averageHandlerUtilization: 0.1, assignmentsLast7Days: 1, recommendationRunsLast30Days: 1 },
  claimsByStatus: [{ label: 'SUBMITTED', value: 1 }], claimsByType: [], workloadDistribution: [], assignmentTrend: [], recentClaims: [], recentActions: [],
  unavailableHandlers: [], system: { claimCenter: 'NOT_CONNECTED', suitabilityEngine: 'ONLINE', database: 'ONLINE' },
};

describe('navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApi.auth.me.mockResolvedValue(supervisor);
    mockApi.dashboard.summary.mockResolvedValue(dashboard);
    mockApi.fairness.summary.mockRejectedValue(new ApiError(500, 'x'));
    mockApi.claims.list.mockResolvedValue(emptyPage);
  });

  it('redirects to sign-in when not authenticated', async () => {
    renderApp(<App />, { signedIn: false });
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('shows the supervisor sections and navigates between them', async () => {
    renderApp(<App />);
    expect(await screen.findByRole('heading', { name: 'Claims Overview' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Main navigation' });
    for (const label of ['Overview Dashboard', 'Triage Queue', 'All Claims', 'Handler Management', 'SmartAssign Routing',
      'Assignment History & Audit', 'Fairness Monitoring', 'Settings']) {
      expect(nav).toHaveTextContent(label);
    }
    await userEvent.click(screen.getByRole('link', { name: /All Claims/ }));
    expect(await screen.findByRole('heading', { name: 'All Claims' })).toBeInTheDocument();
  });

  it('never shows model or algorithm names', async () => {
    renderApp(<App />);
    await screen.findByRole('heading', { name: 'Claims Overview' });
    expect(document.body.textContent).not.toMatch(/random forest|gradient boosting|logistic regression|machine learning|\bROC\b|ROC-AUC|\baccuracy\b/i);
    expect(document.body.textContent).not.toMatch(/synthetic/i);
  });

  it('shows a retry option when the API fails', async () => {
    mockApi.dashboard.summary.mockRejectedValue(new ApiError(0, 'The SmartAssign service could not be reached.'));
    renderApp(<App />);
    expect(await screen.findByText(/could not be reached/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: /Try again/ })).toBeInTheDocument());
  });

  it('keeps customers and handlers out of the supervisor portal', async () => {
    mockApi.auth.me.mockResolvedValue(customer);
    mockApi.customer.list.mockResolvedValue(emptyPage);
    renderApp(<App />, { route: '/claims' });
    expect(await screen.findByText("You haven't reported any claims yet")).toBeInTheDocument();
  });

  it('shows a handler their availability control and no accept or reject buttons', async () => {
    mockApi.auth.me.mockResolvedValue(handlerUser);
    mockApi.handlerPortal.claims.mockResolvedValue(emptyPage);
    mockApi.handlerPortal.me.mockResolvedValue({ resolutionTargetDays: 14, availabilityHistory: [], handler: {
      id: 1, code: 'H001', fullName: 'Rohan Das', email: 'rohan@example.test', phone: null, primaryExpertise: 'AUTO', primaryExpertiseLabel: 'Auto',
      skills: ['AUTO'], experienceYears: 7, currentWorkload: 0, maxWorkload: 10, openSlots: 10, utilization: 0, available: true, unavailableReason: null,
      availabilityChangedAt: null, active: true, location: 'Pune', resolvedCount: 0, successRate: null, avgResolutionDays: null, hasHistory: false,
      source: 'STAFF_REGISTRATION', status: 'AVAILABLE', version: 0, createdAt: '', updatedAt: '' } });
    renderApp(<App />, { route: '/handler' });
    expect(await screen.findByText('Available for new claims')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Unavailable' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /accept|reject/i })).toBeNull();
  });
});
