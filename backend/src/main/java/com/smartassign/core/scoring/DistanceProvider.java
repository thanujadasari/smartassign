package com.smartassign.core.scoring;

import java.util.OptionalDouble;

/** Supplies road distance between cities (derived from the dataset's distance_km column). */
public interface DistanceProvider {

    OptionalDouble distanceKm(String fromCity, String toCity);

    /** Largest known distance; used to normalise proximity and as the value for unknown city pairs. */
    double maxDistanceKm();

    default double distanceOrMax(String fromCity, String toCity) {
        return distanceKm(fromCity, toCity).orElse(maxDistanceKm());
    }
}
