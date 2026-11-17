package com.smartassign.core.fairness;

import com.smartassign.core.HandlerSnapshot;

/** A handler with the number of approved assignments and top recommendations in the monitoring window. */
public record HandlerLoad(HandlerSnapshot handler, int assignmentsInWindow, int topRecommendationsInWindow) {
}
