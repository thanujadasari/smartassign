package com.smartassign.ml;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Internal administrator diagnostics. Not used by the supervisor interface. */
@RestController
@RequestMapping("/api/internal/suitability-service")
@PreAuthorize("hasRole('SUPERVISOR')")
public class InternalMlController {

    private final SuitabilityServiceStatus status;

    public InternalMlController(SuitabilityServiceStatus status) {
        this.status = status;
    }

    @GetMapping
    public Map<String, Object> details() {
        return status.internalDetails();
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        status.refresh();
        return status.internalDetails();
    }
}
