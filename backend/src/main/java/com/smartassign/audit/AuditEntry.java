package com.smartassign.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fluent builder for an audit record. */
public class AuditEntry {

    final String eventType;
    final String entityType;
    final String entityId;
    final String actor;
    String claimNumber;
    String recommendedHandlerCode;
    String finalHandlerCode;
    Double recommendationScore;
    String eligibilitySummary;
    String explanation;
    String supervisorAction;
    String overrideReason;
    String modelVersion;
    final Map<String, Object> details = new LinkedHashMap<>();

    private AuditEntry(String eventType, String entityType, String entityId, String actor) {
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.actor = actor == null ? "system" : actor;
    }

    public static AuditEntry of(String eventType, String entityType, String entityId, String actor) {
        return new AuditEntry(eventType, entityType, entityId, actor);
    }

    public AuditEntry claim(String claimNumber) { this.claimNumber = claimNumber; return this; }
    public AuditEntry recommended(String handlerCode) { this.recommendedHandlerCode = handlerCode; return this; }
    public AuditEntry finalHandler(String handlerCode) { this.finalHandlerCode = handlerCode; return this; }
    public AuditEntry score(Double score) { this.recommendationScore = score; return this; }
    public AuditEntry eligibility(String summary) { this.eligibilitySummary = summary; return this; }
    public AuditEntry explanation(String explanation) { this.explanation = explanation; return this; }
    public AuditEntry action(String action) { this.supervisorAction = action; return this; }
    public AuditEntry overrideReason(String reason) { this.overrideReason = reason; return this; }
    public AuditEntry modelVersion(String version) { this.modelVersion = version; return this; }
    public AuditEntry details(String note) { this.details.put("note", note); return this; }
    public AuditEntry detail(String key, Object value) { this.details.put(key, value); return this; }
}
