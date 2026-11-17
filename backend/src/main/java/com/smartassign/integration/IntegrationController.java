package com.smartassign.integration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/integration/claimcenter")
public class IntegrationController {

    private final ClaimCenterGateway gateway;

    public IntegrationController(ClaimCenterGateway gateway) {
        this.gateway = gateway;
    }

    public record StatusResponse(ClaimCenterGateway.IntegrationStatus status, List<ClaimCenterFieldMapping.Row> claimFields,
                                 List<ClaimCenterFieldMapping.Row> handlerFields, List<String> limitations) {
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(gateway.status(), ClaimCenterFieldMapping.CLAIM_FIELDS,
                ClaimCenterFieldMapping.HANDLER_FIELDS, ClaimCenterFieldMapping.LIMITATIONS);
    }
}
