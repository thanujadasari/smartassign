package com.smartassign.core.fairness;

/**
 * An indicator for supervisor review - NOT an automatic judgement of unfairness. Differences that follow
 * expertise, availability or capacity can be entirely legitimate.
 */
public record FairnessWarning(String type, String severity, String handlerCode, String group, String message) {
}
