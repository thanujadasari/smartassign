import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { emptyPage, mockApi } from './mockApi';
import { claim, excluded, recommendations, supervisor } from './fixtures';

vi.mock('@/lib/api', async (orig) => ({ ...(await orig<typeof import('@/lib/api')>()), api: mockApi }));
import RoutingPage from '@/pages/routing/RoutingPage';
import { renderApp } from './render';

const eligibility = {
  runId: 9, claim, outcome: 'ELIGIBLE_FOUND', eligibleCount: 2, ineligibleCount: 1,
  eligible: recommendations.recommendations.map((r) => ({ handler: r.handler, eligible: true, rules: [], exclusionReasons: null })),
  ineligible: [excluded], exclusionBreakdown: recommendations.exclusionBreakdown, rulesApplied: [{ code: 'EXPERTISE', name: 'Required expertise', param1: null, param2: null }],
  createdAt: new Date().toISOString(),
};
const decided = (status: string, handlerName: string | null) => ({
  id: 5, claimId: 1, claimNumber: 'C00001', runId: 10, recommendedHandler: { id: 1, code: 'A001', fullName: 'Riya Banerjee' },
  assignedHandler: handlerName ? { id: 2, code: 'A002', fullName: handlerName } : null, status, decisionType: null, recommendationScore: 85,
  assignedHandlerScore: 75, explanation: null, overrideReason: status === 'OVERRIDDEN' ? 'Handler already manages the related file' : null,
  reviewReason: null, fallbackReason: null, createdBy: 'supervisor', decidedBy: 'supervisor', decidedAt: new Date().toISOString(), createdAt: new Date().toISOString(),
});

async function toRecommendations() {
  renderApp(<RoutingPage />, { route: '/routing?claimId=1' });
  await userEvent.click(await screen.findByRole('button', { name: /Run eligibility check/ }));
  await userEvent.click(await screen.findByRole('button', { name: /Generate recommendations/ }));
  await screen.findByText(/Ranked eligible handlers/);
}

describe('SmartAssign routing workflow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApi.auth.me.mockResolvedValue(supervisor);
    mockApi.claims.get.mockResolvedValue({ claim, timeline: [] });
    mockApi.claims.list.mockResolvedValue(emptyPage);
    mockApi.routing.eligibility.mockResolvedValue(eligibility);
    mockApi.routing.recommend.mockResolvedValue(recommendations);
    mockApi.assignments.create.mockResolvedValue(decided('PENDING', null));
  });

  it('runs eligibility, ranks only eligible handlers and approves the recommendation', async () => {
    mockApi.assignments.approve.mockResolvedValue({ ...decided('APPROVED', 'Riya Banerjee') });
    await toRecommendations();
    expect(screen.getByText('Recommended because this handler is based in the same city as the loss (Pune).')).toBeInTheDocument();
    // the excluded handler appears only in the eligibility table, never as a selectable recommendation
    const ranked = screen.getAllByRole('article');
    expect(ranked).toHaveLength(2);
    expect(ranked.some((a) => within(a).queryByText('Excluded Person'))).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: /Approve recommendation/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirm assignment' }));
    expect(mockApi.assignments.create).toHaveBeenCalledWith(1, 10);
    expect(mockApi.assignments.approve).toHaveBeenCalledWith(5, undefined);
    expect(await screen.findByText(/Assigned to Riya Banerjee/)).toBeInTheDocument();
  });

  it('requires a reason before overriding with another eligible handler', async () => {
    mockApi.assignments.override.mockResolvedValue(decided('OVERRIDDEN', 'Anjali Joshi'));
    await toRecommendations();
    await userEvent.click(screen.getByRole('button', { name: 'Select this handler instead' }));
    await userEvent.click(screen.getByRole('button', { name: /Assign selected handler/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirm override' }));
    expect(await screen.findByText('Please enter at least 10 characters')).toBeInTheDocument();
    expect(mockApi.assignments.override).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText(/Override reason/), 'Handler already manages the related file');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm override' }));
    expect(mockApi.assignments.override).toHaveBeenCalledWith(5, 2, 'Handler already manages the related file');
    expect(await screen.findByText(/Assigned to Anjali Joshi/)).toBeInTheDocument();
  });

  it('sends a claim for manual review with a reason', async () => {
    mockApi.assignments.manualReview.mockResolvedValue(decided('MANUAL_REVIEW', null));
    await toRecommendations();
    await userEvent.click(screen.getByRole('button', { name: /Send for manual review/ }));
    await userEvent.type(await screen.findByLabelText(/Reason for manual review/), 'Coverage check with underwriting');
    await userEvent.click(screen.getByRole('button', { name: 'Send for review' }));
    expect(mockApi.assignments.manualReview).toHaveBeenCalledWith(5, 'Coverage check with underwriting');
    expect(await screen.findByText('Claim sent for manual review')).toBeInTheDocument();
  });

  it('shows the safe fallback when no handler is eligible', async () => {
    mockApi.routing.recommend.mockResolvedValue({
      ...recommendations, outcome: 'NO_ELIGIBLE_HANDLER', recommendations: [], eligibleCount: 0, scoringBasis: 'NONE',
      fallback: { reason: 'No handler meets every mandatory requirement. Eligibility rules were not relaxed.', claimStatus: 'MANUAL_REVIEW', assignmentId: 3, newlyQueued: true },
    });
    renderApp(<RoutingPage />, { route: '/routing?claimId=1' });
    await userEvent.click(await screen.findByRole('button', { name: /Run eligibility check/ }));
    await userEvent.click(await screen.findByRole('button', { name: /Generate recommendations/ }));
    expect(await screen.findByText(/safe fallback applied/)).toBeInTheDocument();
    expect(screen.getByText(/rules were not relaxed/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Approve recommendation/ })).not.toBeInTheDocument();
  });

  it('shows the server reason when a decision is rejected', async () => {
    const { ApiError } = await import('@/lib/api');
    mockApi.assignments.approve.mockRejectedValue(new ApiError(422, 'The recommended handler (A001) is no longer eligible.', {}, ['Currently unavailable']));
    await toRecommendations();
    await userEvent.click(screen.getByRole('button', { name: /Approve recommendation/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirm assignment' }));
    expect(await screen.findByText(/no longer eligible\. Currently unavailable/)).toBeInTheDocument();
  });
});
