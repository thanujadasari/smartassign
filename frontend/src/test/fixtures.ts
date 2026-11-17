import type { Claim, EligibilityRow, HandlerSummary, Recommendation, RecommendationResponse, User } from '@/lib/types';

const person = (id: number, fullName: string) => ({ id, fullName, email: `${fullName.split(' ')[0].toLowerCase()}@example.test`, phone: null });

export const supervisor: User = { id: 1, fullName: 'Sunita Rao', email: 'sunita@example.test', phone: null, role: 'SUPERVISOR', handlerId: null, lastLoginAt: null };
export const customer: User = { id: 2, fullName: 'Asha Kulkarni', email: 'asha@example.test', phone: null, role: 'CUSTOMER', handlerId: null, lastLoginAt: null };
export const handlerUser: User = { id: 3, fullName: 'Rohan Das', email: 'rohan@example.test', phone: null, role: 'HANDLER', handlerId: 1, lastLoginAt: null };

export const claim: Claim = {
  id: 1, claimNumber: 'CLM-000001', claimType: 'LIABILITY', claimTypeLabel: 'Liability', policyType: 'GENERAL_LIABILITY', policyNumber: 'P1001',
  incidentType: 'ACCIDENT', incidentDate: '2026-10-30', location: 'Pune', estimatedLoss: 910398, injuryInvolved: true, thirdPartyInvolved: false, ongoingRisk: false,
  priority: { priority: 'HIGH', suggestedPriority: 'HIGH', suggestedReasons: [{ text: 'Someone was injured', basis: 'ASSUMED' }], note: null, setBy: null, setAt: null },
  description: 'Visitor slipped on a wet floor in the shop and was injured.', contactPhone: null, severity: 'CRITICAL', complexity: 'LOW',
  fraudRiskScore: 0.086, claimAgeDays: 4, previousReassignments: 0, status: 'UNASSIGNED', customer: person(2, 'Asha Kulkarni'),
  triage: { suggestedSeverity: 'CRITICAL', suggestedComplexity: 'LOW', suggestedFraudRisk: 0.1, note: null, triagedBy: person(1, 'Sunita Rao'), triagedAt: '2026-11-01T00:00:00Z' },
  assignedHandler: null, assignedBy: null, assignedAt: null, resolvedAt: null, resolutionNote: null, resolutionDays: null,
  source: 'CUSTOMER_PORTAL', version: 0, createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z',
};

export const handler = (id: number, name: string, location = 'Pune'): HandlerSummary => ({
  id, code: `A${String(id).padStart(3, '0')}`, fullName: name, primaryExpertise: 'LIABILITY', expertiseLabel: 'Liability', location,
  experienceYears: 10, currentWorkload: 2, maxWorkload: 10, successRate: 0.8, similarClaimsHandled: 60, hasHistory: true, available: true, active: true,
});

export const excluded: EligibilityRow = {
  handler: { ...handler(99, 'Excluded Person', 'Delhi'), primaryExpertise: 'AUTO', expertiseLabel: 'Auto' }, eligible: false,
  rules: [{ code: 'EXPERTISE', name: 'Required expertise', passed: false, reason: 'Required expertise does not match (needs Liability, handler is Auto)' }],
  exclusionReasons: 'Required expertise does not match (needs Liability, handler is Auto)',
};

const rec = (rank: number, h: HandlerSummary, score: number): Recommendation => ({
  rank, handler: h, suitabilityScore: score, scoreBeforeFairness: score, fairnessAdjusted: false, distanceKm: 1, sameCity: true,
  explanation: `${rank === 1 ? 'Recommended' : `Ranked #${rank}`} because this handler is based in the same city as the loss (Pune).`,
  factors: [{ factor: 'PROXIMITY', label: 'Location proximity', value: 1, weight: 15, points: 15, detail: 'Based in Pune (same city as the loss)' }],
});

export const recommendations: RecommendationResponse = {
  runId: 10, claim, outcome: 'ELIGIBLE_FOUND', scoringBasis: 'PROFILE_ONLY', scoringNote: 'Predicted claim fit is temporarily unavailable.',
  weightsApplied: { 'Location proximity': 15 }, recommendations: [rec(1, handler(1, 'Riya Banerjee'), 85), rec(2, handler(2, 'Anjali Joshi'), 75)],
  eligibleCount: 2, ineligibleCount: 1, ineligible: [excluded], exclusionBreakdown: [{ ruleCode: 'EXPERTISE', ruleName: 'Required expertise', handlers: 1 }],
  fallback: null, validUntil: new Date(Date.now() + 3600_000).toISOString(), createdAt: new Date().toISOString(),
};
