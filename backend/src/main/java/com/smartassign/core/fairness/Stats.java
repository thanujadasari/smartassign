package com.smartassign.core.fairness;

import java.util.List;

/** Small statistics helpers (population standard deviation, Gini coefficient). */
public final class Stats {

    private Stats() {
    }

    public static double mean(List<? extends Number> xs) {
        return xs.isEmpty() ? 0 : xs.stream().mapToDouble(Number::doubleValue).average().orElse(0);
    }

    public static double stdDev(List<? extends Number> xs) {
        if (xs.size() < 2) return 0;
        double m = mean(xs);
        double ss = xs.stream().mapToDouble(x -> Math.pow(x.doubleValue() - m, 2)).sum();
        return Math.sqrt(ss / xs.size());
    }

    /** Gini coefficient of non-negative values; 0 = perfectly equal, approaching 1 = concentrated. */
    public static double gini(List<? extends Number> xs) {
        int n = xs.size();
        if (n == 0) return 0;
        double[] v = xs.stream().mapToDouble(Number::doubleValue).sorted().toArray();
        double sum = 0;
        double weighted = 0;
        for (int i = 0; i < n; i++) {
            sum += v[i];
            weighted += (i + 1) * v[i];
        }
        if (sum == 0) return 0;
        return (2.0 * weighted) / (n * sum) - (n + 1.0) / n;
    }
}
