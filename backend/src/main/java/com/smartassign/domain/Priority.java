package com.smartassign.domain;

/**
 * How urgently a claim needs attention. Suggested by rules, decided by the supervisor.
 * rank() is used for ordering queues (most urgent first).
 */
public enum Priority {
    HIGH(1), MEDIUM(2), LOW(3);

    private final int rank;

    Priority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
