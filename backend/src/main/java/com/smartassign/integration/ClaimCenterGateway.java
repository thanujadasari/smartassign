package com.smartassign.integration;

import java.util.List;
import java.util.Map;

/**
 * Boundary to Guidewire ClaimCenter, the external system of record. The intended implementation uses the
 * customer's approved ClaimCenter APIs (e.g. Cloud API) or scheduled data exports. No implementation that talks to a
 * real ClaimCenter is shipped: credentials, endpoints and entity extensions differ per installation and must be
 * configured and tested with the customer's Guidewire team.
 */
public interface ClaimCenterGateway {

    IntegrationStatus status();

    /** Pull new or changed claims since the given ISO timestamp (mapped with {@link ClaimCenterFieldMapping}). */
    List<Map<String, Object>> fetchClaimsChangedSince(String isoTimestamp);

    /** Pull adjuster (user) profiles with skills, workload and availability. */
    List<Map<String, Object>> fetchHandlers();

    /** Optional write-back of a supervisor-approved assignment. Must be idempotent on the ClaimCenter side. */
    void writeBackAssignment(String claimNumber, String handlerCode, String reason);

    record IntegrationStatus(boolean configured, String mode, String message) {
    }
}
