package com.smartassign.auth;

/**
 * CUSTOMER    registers claims and follows their progress
 * SUPERVISOR  triages claims, runs eligibility + ranking, assigns handlers, manages handlers and settings
 * HANDLER     sees the claims assigned to them, sets their own availability, marks claims resolved
 */
public enum Role { CUSTOMER, SUPERVISOR, HANDLER }
