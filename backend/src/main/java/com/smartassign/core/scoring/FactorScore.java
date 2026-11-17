package com.smartassign.core.scoring;

/**
 * One factor's contribution.
 * value: normalised 0..1 factor value; weight: effective weight in percent;
 * points: value * weight (0..weight); detail: business-language description.
 */
public record FactorScore(Factor factor, String label, double value, double weight, double points, String detail) {
}
