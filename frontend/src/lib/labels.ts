// Supervisor-facing wording for codes returned by the API.

export const CLAIM_TYPE_LABEL: Record<string, string> = {
  AUTO: 'Auto',
  PROPERTY: 'Property',
  LIABILITY: 'Liability',
  WORKERS_COMPENSATION: "Workers' Compensation",
  MULTI_P_AND_C: 'Multi-line P&C',
};

export const CLAIM_STATUS_LABEL: Record<string, string> = {
  SUBMITTED: 'Awaiting triage',
  UNASSIGNED: 'Ready to assign',
  ASSIGNED: 'Assigned',
  MANUAL_REVIEW: 'Manual review',
  RESOLVED: 'Resolved',
  // customer-facing summary states
  IN_REVIEW: 'Under review',
};

export const INCIDENT_LABEL = (v: string) => v.charAt(0) + v.slice(1).toLowerCase().replace(/_/g, ' ');

export const HANDLER_STATUS_LABEL: Record<string, string> = {
  AVAILABLE: 'Available',
  UNAVAILABLE: 'Unavailable',
  INACTIVE: 'Inactive',
  AT_CAPACITY: 'At capacity',
  OVER_CAPACITY: 'Over capacity',
  BALANCED: 'Balanced',
  HIGH_UTILIZATION: 'High utilisation',
};

export const EVENT_LABEL: Record<string, string> = {
  LOGIN: 'Signed in',
  LOGIN_FAILED: 'Failed sign-in',
  LOGOUT: 'Signed out',
  CLAIM_SUBMITTED: 'Claim registered by customer',
  CLAIM_TRIAGED: 'Claim triaged',
  CLAIM_RETRIAGED: 'Claim re-triaged',
  CLAIM_RESOLVED: 'Claim resolved by handler',
  HANDLER_CREATED: 'Handler registered',
  HANDLER_UPDATED: 'Handler updated',
  STAFF_SIGNUP_REJECTED: 'Staff sign-up rejected',
  HANDLER_AVAILABILITY_CHANGED: 'Availability changed',
  HANDLER_STATUS_CHANGED: 'Handler status changed',
  ELIGIBILITY_CHECKED: 'Eligibility checked',
  RECOMMENDATIONS_GENERATED: 'Recommendations generated',
  ASSIGNMENT_APPROVED: 'Recommendation approved',
  ASSIGNMENT_OVERRIDDEN: 'Recommendation overridden',
  MANUAL_REVIEW_REQUESTED: 'Sent for manual review',
  FALLBACK_MANUAL_REVIEW: 'No eligible handler — manual review',
  ROUTING_NO_ELIGIBLE_HANDLER: 'No eligible handler found',
  SETTINGS_WEIGHTS_UPDATED: 'Ranking weights updated',
  ELIGIBILITY_RULE_UPDATED: 'Eligibility rule updated',
  CLAIM_PRIORITY_CHANGED: 'Claim priority changed',
  USER_REGISTERED: 'User registered',
};

export const STRATEGY_COLOR: Record<string, string> = {
  ROUND_ROBIN: '#9cb1d2',
  WEIGHTED_WORKLOAD: '#3b7ddd',
  SMARTASSIGN: '#0f1f3a',
};

export const CHART = {
  navy: '#0f1f3a',
  blue: '#3b7ddd',
  sky: '#9cb1d2',
  slate: '#c5d2e6',
  green: '#15803d',
  amber: '#d97706',
  red: '#b91c1c',
  grid: '#e2e7ee',
  axis: '#8693a3',
};
