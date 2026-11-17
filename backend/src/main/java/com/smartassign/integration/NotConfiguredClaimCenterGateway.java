package com.smartassign.integration;

import com.smartassign.common.ApiException;
import com.smartassign.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Default gateway: the application runs standalone on claims registered in its own customer portal. No external calls are made. */
@Component
public class NotConfiguredClaimCenterGateway implements ClaimCenterGateway {

    private final AppProperties props;

    public NotConfiguredClaimCenterGateway(AppProperties props) {
        this.props = props;
    }

    @Override
    public IntegrationStatus status() {
        boolean flagged = props.claimcenter() != null && props.claimcenter().enabled();
        return new IntegrationStatus(false, "NOT_CONNECTED", flagged
                ? "ClaimCenter integration is switched on in configuration, but no verified connector is installed. "
                + "SmartAssign continues to use claims registered in its customer portal."
                : "Not connected. SmartAssign is running standalone on claims registered in its customer portal.");
    }

    @Override
    public List<Map<String, Object>> fetchClaimsChangedSince(String isoTimestamp) {
        throw notConfigured();
    }

    @Override
    public List<Map<String, Object>> fetchHandlers() {
        throw notConfigured();
    }

    @Override
    public void writeBackAssignment(String claimNumber, String handlerCode, String reason) {
        throw notConfigured();
    }

    private static ApiException notConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ClaimCenter integration is not configured");
    }
}
