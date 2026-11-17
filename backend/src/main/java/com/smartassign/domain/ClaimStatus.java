package com.smartassign.domain;

/**
 * SUBMITTED      registered by the customer, waiting for supervisor triage
 * UNASSIGNED     triaged (severity / complexity / fraud risk confirmed by a supervisor), ready to route
 * ASSIGNED       assigned to a handler by a supervisor
 * MANUAL_REVIEW  no eligible handler, or the supervisor sent it for manual review
 * RESOLVED       the handler marked it resolved
 */
public enum ClaimStatus { SUBMITTED, UNASSIGNED, ASSIGNED, MANUAL_REVIEW, RESOLVED }
