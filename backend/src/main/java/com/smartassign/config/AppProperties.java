package com.smartassign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** All application-specific settings (prefix "smartassign"). Secrets come from environment variables. */
@ConfigurationProperties(prefix = "smartassign")
public record AppProperties(Jwt jwt, Cors cors, Ml ml, Registration registration, Claims claims, Routing routing,
                            ClaimCenter claimcenter) {

    public record Jwt(String secret, long expirationMinutes, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** Internal suitability (ML) service. Never called by the frontend directly. */
    public record Ml(boolean enabled, String baseUrl, int connectTimeoutMs, int readTimeoutMs, int healthCacheSeconds) {
    }

    /** staffCode: shared secret supervisors and handlers enter when signing up. Blank = staff sign-up disabled. */
    public record Registration(String staffCode) {
        public boolean staffSignupEnabled() {
            return staffCode != null && !staffCode.isBlank();
        }
    }

    /** resolutionTargetDays: a claim resolved within this many days of assignment counts as resolved on time. */
    public record Claims(int resolutionTargetDays) {
    }

    /** recommendationValidityMinutes: how long a recommendation run may be acted upon before it must be regenerated. */
    public record Routing(int recommendationValidityMinutes, int fairnessWindowDays) {
    }

    /** Guidewire ClaimCenter integration. Disabled until approved credentials are configured and tested. */
    public record ClaimCenter(boolean enabled, String baseUrl, String authMode) {
    }
}
