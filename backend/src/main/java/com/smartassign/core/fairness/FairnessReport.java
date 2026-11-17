package com.smartassign.core.fairness;

import java.util.List;

public record FairnessReport(
        int activeHandlers,
        int availableHandlers,
        double averageUtilization,
        double utilizationStdDev,
        double assignmentGini,
        int overCapacityHandlers,
        int atCapacityHandlers,
        int highUtilizationHandlers,
        double highUtilizationThreshold,
        List<GroupStats> groups,
        List<HandlerFairness> handlers,
        List<FairnessWarning> warnings) {

    public record GroupStats(String expertise, String label, int handlers, int totalCapacity, int totalWorkload,
                             double averageUtilization, double utilizationCoefficientOfVariation,
                             int assignmentsInWindow) {
    }

    public record HandlerFairness(String handlerCode, String name, String expertise, String location, boolean active,
                                  boolean available, int currentWorkload, int maxWorkload, double utilization,
                                  int assignmentsInWindow, double expectedAssignmentsByCapacity,
                                  int topRecommendationsInWindow, String status) {
    }
}
