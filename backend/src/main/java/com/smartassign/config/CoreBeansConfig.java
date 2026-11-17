package com.smartassign.config;

import com.smartassign.core.eligibility.EligibilityEngine;
import com.smartassign.core.fairness.FairnessCalculator;
import com.smartassign.core.scoring.DistanceProvider;
import com.smartassign.core.scoring.ScoringEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free routing core into Spring. */
@Configuration
public class CoreBeansConfig {

    @Bean
    public EligibilityEngine eligibilityEngine() {
        return new EligibilityEngine();
    }

    @Bean
    public ScoringEngine scoringEngine(DistanceProvider distanceProvider) {
        return new ScoringEngine(distanceProvider);
    }

    @Bean
    public FairnessCalculator fairnessCalculator() {
        return new FairnessCalculator();
    }
}
