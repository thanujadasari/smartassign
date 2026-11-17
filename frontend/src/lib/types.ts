// Types mirror the Spring Boot DTOs exactly (see documentation/03-api-reference.md).

export type Role = 'CUSTOMER' | 'SUPERVISOR' | 'HANDLER';
export type Priority = 'HIGH' | 'MEDIUM' | 'LOW';
export type ClaimStatus = 'SUBMITTED' | 'UNASSIGNED' | 'ASSIGNED' | 'MANUAL_REVIEW' | 'RESOLVED';
export type ClaimType = 'AUTO' | 'PROPERTY' | 'LIABILITY' | 'WORKERS_COMPENSATION';
export type Expertise = ClaimType | 'MULTI_P_AND_C';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type Complexity = 'LOW' | 'MEDIUM' | 'HIGH';
export type HandlerStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'INACTIVE' | 'AT_CAPACITY' | 'OVER_CAPACITY';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error?: string;
  message: string;
  path?: string;
  fieldErrors?: { field: string; message: string }[];
  details?: string[];
}

export interface User {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  role: Role;
  handlerId: number | null;
  lastLoginAt: string | null;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface PersonRef {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
}

export interface HandlerRef {
  id: number;
  code: string;
  fullName: string;
  email: string | null;
  phone: string | null;
}

export interface PriorityInfo {
  /** decided by the supervisor; null until triage */
  priority: Priority | null;
  suggestedPriority: Priority | null;
  suggestedReasons: TriageReason[];
  note: string | null;
  setBy: PersonRef | null;
  setAt: string | null;
}

export interface TriageInfo {
  suggestedSeverity: Severity | null;
  suggestedComplexity: Complexity | null;
  suggestedFraudRisk: number | null;
  note: string | null;
  triagedBy: PersonRef | null;
  triagedAt: string | null;
}

/** Full claim (supervisor and assigned handler). severity/complexity/fraudRiskScore are null until triage. */
export interface Claim {
  id: number;
  claimNumber: string;
  claimType: ClaimType;
  claimTypeLabel: string;
  policyType: string;
  policyNumber: string;
  incidentType: string;
  incidentDate: string;
  location: string;
  estimatedLoss: number;
  injuryInvolved: boolean;
  thirdPartyInvolved: boolean;
  ongoingRisk: boolean;
  description: string;
  contactPhone: string | null;
  priority: PriorityInfo;
  severity: Severity | null;
  complexity: Complexity | null;
  fraudRiskScore: number | null;
  claimAgeDays: number;
  previousReassignments: number;
  status: ClaimStatus;
  customer: PersonRef;
  triage: TriageInfo | null;
  assignedHandler: HandlerRef | null;
  assignedBy: PersonRef | null;
  assignedAt: string | null;
  resolvedAt: string | null;
  resolutionNote: string | null;
  resolutionDays: number | null;
  source: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface StatusEvent {
  fromStatus: string | null;
  toStatus: string;
  note: string | null;
  actorName: string;
  actorRole: string;
  at: string;
}

export interface ClaimDetail {
  claim: Claim;
  timeline: StatusEvent[];
}

export interface TriageReason {
  text: string;
  basis: 'DATA' | 'ASSUMED';
}

export interface FraudFlag {
  code: string;
  text: string;
  weight: number;
}

export interface TriageSuggestion {
  severity: Severity;
  severityReasons: TriageReason[];
  complexity: Complexity;
  complexityReasons: TriageReason[];
  fraudRiskScore: number;
  fraudFlags: FraudFlag[];
  fraudNote: string;
  priority: Priority;
  priorityReasons: TriageReason[];
}

export interface TriageInput {
  priority: Priority;
  severity: Severity;
  complexity: Complexity;
  fraudRiskScore: number;
  note?: string;
}

export type CustomerStatus = 'SUBMITTED' | 'IN_REVIEW' | 'ASSIGNED' | 'RESOLVED';

/** What a customer sees - no internal triage values. */
export interface CustomerClaim {
  id: number;
  claimNumber: string;
  claimType: ClaimType;
  claimTypeLabel: string;
  policyType: string;
  policyNumber: string;
  incidentType: string;
  incidentDate: string;
  location: string;
  estimatedLoss: number;
  injuryInvolved: boolean;
  thirdPartyInvolved: boolean;
  ongoingRisk: boolean;
  description: string;
  contactPhone: string | null;
  status: CustomerStatus;
  statusLabel: string;
  handler: HandlerRef | null;
  assignedAt: string | null;
  resolvedAt: string | null;
  resolutionNote: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerClaimDetail {
  claim: CustomerClaim;
  timeline: StatusEvent[];
}

export interface CustomerClaimInput {
  claimType: ClaimType;
  policyType: string;
  policyNumber: string;
  incidentType: string;
  incidentDate: string;
  location: string;
  estimatedLoss: number;
  injuryInvolved: boolean;
  thirdPartyInvolved: boolean;
  ongoingRisk: boolean;
  description: string;
  contactPhone?: string;
}

export interface CustomerSignupInput {
  fullName: string;
  email: string;
  phone?: string;
  password: string;
}

export interface StaffSignupInput extends CustomerSignupInput {
  role: 'SUPERVISOR' | 'HANDLER';
  staffCode: string;
  handlerProfile?: { primaryExpertise: Expertise; experienceYears: number; location: string };
}

export interface Handler {
  id: number;
  code: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  primaryExpertise: Expertise;
  primaryExpertiseLabel: string;
  skills: ClaimType[];
  experienceYears: number;
  currentWorkload: number;
  maxWorkload: number;
  openSlots: number;
  utilization: number;
  available: boolean;
  unavailableReason: string | null;
  availabilityChangedAt: string | null;
  active: boolean;
  location: string;
  resolvedCount: number;
  /** null until the handler has resolved a claim */
  successRate: number | null;
  avgResolutionDays: number | null;
  hasHistory: boolean;
  source: string;
  status: HandlerStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface HandlerUpdateInput {
  primaryExpertise: Expertise;
  additionalSkills?: ClaimType[];
  experienceYears: number;
  maxWorkload: number;
  location: string;
}

export interface HandlerClaimRow {
  claimId: number;
  claimNumber: string;
  claimType: string;
  severity: string | null;
  location: string;
  status: string;
  assignedByName: string | null;
  assignedAt: string | null;
  resolvedAt: string | null;
}

export interface AvailabilityEvent {
  available: boolean;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface HandlerDetail {
  handler: Handler;
  openClaims: HandlerClaimRow[];
  resolvedClaims: HandlerClaimRow[];
  availabilityHistory: AvailabilityEvent[];
  totalAssignments: number;
  overridesReceived: number;
  recommendationsAsTopChoice: number;
}

export interface HandlerProfile {
  handler: Handler;
  resolutionTargetDays: number;
  availabilityHistory: AvailabilityEvent[];
}

export interface HandlerSummary {
  id: number;
  code: string;
  fullName: string;
  primaryExpertise: Expertise;
  expertiseLabel: string;
  location: string;
  experienceYears: number;
  currentWorkload: number;
  maxWorkload: number;
  successRate: number | null;
  similarClaimsHandled: number | null;
  hasHistory: boolean;
  available: boolean;
  active: boolean;
}

export interface RuleResult {
  code: string;
  name: string;
  passed: boolean;
  reason: string;
}

export interface EligibilityRow {
  handler: HandlerSummary;
  eligible: boolean;
  rules: RuleResult[];
  exclusionReasons: string | null;
}

export interface ExclusionCount {
  ruleCode: string;
  ruleName: string;
  handlers: number;
}

export interface EligibilityResponse {
  runId: number;
  claim: Claim;
  outcome: 'ELIGIBLE_FOUND' | 'NO_ELIGIBLE_HANDLER';
  eligibleCount: number;
  ineligibleCount: number;
  eligible: EligibilityRow[];
  ineligible: EligibilityRow[];
  exclusionBreakdown: ExclusionCount[];
  rulesApplied: { code: string; name: string; param1: number | null; param2: number | null }[];
  createdAt: string;
}

export interface Factor {
  factor: string;
  label: string;
  value: number;
  weight: number;
  points: number;
  detail: string;
}

export interface Recommendation {
  rank: number;
  handler: HandlerSummary;
  suitabilityScore: number;
  scoreBeforeFairness: number;
  fairnessAdjusted: boolean;
  factors: Factor[];
  explanation: string;
  distanceKm: number;
  sameCity: boolean;
}

export interface RecommendationResponse {
  runId: number;
  claim: Claim;
  outcome: 'ELIGIBLE_FOUND' | 'NO_ELIGIBLE_HANDLER';
  scoringBasis: 'PROFILE_AND_PREDICTED_FIT' | 'PROFILE_ONLY' | 'NONE';
  scoringNote: string | null;
  weightsApplied: Record<string, number>;
  recommendations: Recommendation[];
  eligibleCount: number;
  ineligibleCount: number;
  ineligible: EligibilityRow[];
  exclusionBreakdown: ExclusionCount[];
  fallback: { reason: string; claimStatus: string; assignmentId: number | null; newlyQueued: boolean } | null;
  validUntil: string | null;
  createdAt: string;
}

export interface Assignment {
  id: number;
  claimId: number;
  claimNumber: string | null;
  runId: number | null;
  recommendedHandler: HandlerRef | null;
  assignedHandler: HandlerRef | null;
  status: 'PENDING' | 'APPROVED' | 'OVERRIDDEN' | 'MANUAL_REVIEW' | 'CANCELLED';
  decisionType: string | null;
  recommendationScore: number | null;
  assignedHandlerScore: number | null;
  explanation: string | null;
  overrideReason: string | null;
  reviewReason: string | null;
  fallbackReason: string | null;
  createdBy: string;
  decidedBy: string | null;
  decidedAt: string | null;
  createdAt: string;
}

export interface AuditEntry {
  id: number;
  eventType: string;
  entityType: string;
  entityId: string | null;
  claimNumber: string | null;
  recommendedHandlerCode: string | null;
  finalHandlerCode: string | null;
  recommendationScore: number | null;
  eligibilitySummary: string | null;
  explanation: string | null;
  supervisorAction: string | null;
  overrideReason: string | null;
  actorUsername: string;
  details: string | null;
  createdAt: string;
}

export interface LabelValue {
  label: string;
  value: number;
}

export interface DashboardSummary {
  kpis: {
    totalClaims: number;
    awaitingTriage: number;
    readyToAssign: number;
    assignedClaims: number;
    manualReviewClaims: number;
    resolvedClaims: number;
    availableHandlers: number;
    activeHandlers: number;
    unavailableHandlers: number;
    eligibleAssignmentRate: number | null;
    averageHandlerUtilization: number;
    assignmentsLast7Days: number;
    recommendationRunsLast30Days: number;
  };
  claimsByStatus: LabelValue[];
  claimsByType: LabelValue[];
  workloadDistribution: LabelValue[];
  assignmentTrend: { date: string; approved: number; overridden: number; manualReview: number }[];
  recentClaims: Claim[];
  recentActions: AuditEntry[];
  unavailableHandlers: { id: number; code: string; fullName: string; reason: string | null; since: string | null }[];
  system: {
    claimCenter: string;
    suitabilityEngine: 'ONLINE' | 'OFFLINE' | 'DISABLED';
    database: string;
  };
}

export interface FairnessWarning {
  type: string;
  severity: 'CRITICAL' | 'WARNING' | 'INFO';
  handlerCode: string | null;
  group: string | null;
  message: string;
}

export interface FairnessSummary {
  windowDays: number;
  generatedAt: string;
  note: string;
  report: {
    activeHandlers: number;
    availableHandlers: number;
    averageUtilization: number;
    utilizationStdDev: number;
    assignmentGini: number;
    overCapacityHandlers: number;
    atCapacityHandlers: number;
    highUtilizationHandlers: number;
    highUtilizationThreshold: number;
    groups: {
      expertise: string;
      label: string;
      handlers: number;
      totalCapacity: number;
      totalWorkload: number;
      averageUtilization: number;
      utilizationCoefficientOfVariation: number;
      assignmentsInWindow: number;
    }[];
    handlers: {
      handlerCode: string;
      name: string;
      expertise: string;
      location: string;
      active: boolean;
      available: boolean;
      currentWorkload: number;
      maxWorkload: number;
      utilization: number;
      assignmentsInWindow: number;
      expectedAssignmentsByCapacity: number;
      topRecommendationsInWindow: number;
      status: string;
    }[];
    warnings: FairnessWarning[];
  };
  utilizationDistribution: { label: string; handlers: number }[];
  assignmentsByExpertise: Record<string, number>;
  topRecommendationsByExpertise: Record<string, number>;
}

export interface RankingWeights {
  id: number;
  expertise: number;
  workload: number;
  experience: number;
  successRate: number;
  similarClaims: number;
  proximity: number;
  predictedFit: number;
  total: number;
  fairnessThreshold: number;
  fairnessPenalty: number;
  usePredictedFit: boolean;
  active: boolean;
  updatedBy: string | null;
  updatedAt: string;
}

export type WeightsInput = Omit<RankingWeights, 'id' | 'total' | 'active' | 'updatedBy' | 'updatedAt'>;

export interface EligibilityRule {
  code: string;
  name: string;
  description: string;
  category: 'CORE' | 'BUSINESS';
  enabled: boolean;
  locked: boolean;
  param1: number | null;
  param2: number | null;
  param1Label: string | null;
  param2Label: string | null;
  updatedBy: string | null;
  updatedAt: string;
}

export interface Option {
  value: string;
  label: string;
}

export interface ReferenceOptions {
  claimTypes: Option[];
  policyTypes: Option[];
  severities: Option[];
  complexities: Option[];
  incidentTypes: Option[];
  expertise: Option[];
  claimStatuses: Option[];
  cities: string[];
  policyTypesByClaimType: Record<string, string[]>;
  incidentTypesByClaimType: Record<string, string[]>;
}

export interface IntegrationStatus {
  status: { configured: boolean; mode: string; message: string };
  claimFields: { smartAssignField: string; claimCenterSource: string; direction: string; notes: string }[];
  handlerFields: { smartAssignField: string; claimCenterSource: string; direction: string; notes: string }[];
  limitations: string[];
}
