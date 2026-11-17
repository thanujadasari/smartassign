package com.smartassign.core;

import com.smartassign.core.scoring.Factor;
import com.smartassign.core.scoring.FactorScore;
import com.smartassign.core.scoring.RankingResult;
import com.smartassign.core.scoring.RankingWeights;
import com.smartassign.core.scoring.ScoredCandidate;
import com.smartassign.core.scoring.ScoringEngine;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Expertise;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.smartassign.core.Fixtures.claim;
import static com.smartassign.core.Fixtures.handler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine(Fixtures.distances());

    @Test
    void weightsMustTotal100() {
        RankingWeights.defaults().validate();
        assertThatThrownBy(() -> new RankingWeights(10, 10, 10, 10, 10, 10, 10, 0.85, 0.15, true).validate())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("total 100");
        assertThatThrownBy(() -> new RankingWeights(-5, 25, 20, 15, 10, 15, 20, 0.85, 0.15, true).validate())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mlWeightIsRedistributedWhenPredictionsUnavailable() {
        Map<Factor, Double> eff = RankingWeights.defaults().effective(false);
        assertThat(eff.get(Factor.ML_SUITABILITY)).isZero();
        assertThat(eff.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(100.0, within(1e-9));
        assertThat(eff.get(Factor.WORKLOAD)).isCloseTo(25.0, within(1e-9)); // 20 * 100/80
    }

    @Test
    void rankingIsByScoreAndFactorPointsAddUp() {
        var claim = claim(ClaimType.AUTO);
        var idle = handler(1, Expertise.AUTO, 0, 10);
        var busy = handler(2, Expertise.AUTO, 8, 10);
        RankingResult r = engine.rank(claim, List.of(busy, idle), RankingWeights.defaults(), Map.of());
        assertThat(r.candidates()).extracting(c -> c.handler().code()).containsExactly("A001", "A002");
        assertThat(r.candidates().get(0).rank()).isEqualTo(1);
        for (ScoredCandidate c : r.candidates()) {
            double sum = c.factors().stream().mapToDouble(FactorScore::points).sum();
            assertThat(sum).isCloseTo(c.scoreBeforeFairness(), within(0.1));
            assertThat(c.totalScore()).isBetween(0.0, 100.0);
        }
    }

    @Test
    void predictedFitUsedOnlyWhenEveryCandidateHasAScore() {
        var claim = claim(ClaimType.AUTO);
        var a = handler(1, Expertise.AUTO, 0, 10);
        var b = handler(2, Expertise.AUTO, 0, 10);
        assertThat(engine.rank(claim, List.of(a, b), RankingWeights.defaults(), Map.of(1L, 0.9)).mlUsed()).isFalse();
        RankingResult full = engine.rank(claim, List.of(a, b), RankingWeights.defaults(), Map.of(1L, 0.9, 2L, 0.1));
        assertThat(full.mlUsed()).isTrue();
        assertThat(full.candidates().get(0).handler().code()).isEqualTo("A001");
    }

    @Test
    void predictedFitIgnoredWhenDisabledInSettings() {
        RankingWeights noMl = new RankingWeights(10, 20, 10, 15, 10, 15, 20, 0.85, 0.15, false);
        var r = engine.rank(claim(ClaimType.AUTO), List.of(handler(1, Expertise.AUTO, 0, 10)), noMl, Map.of(1L, 0.9));
        assertThat(r.mlUsed()).isFalse();
        assertThat(r.candidates().get(0).factors()).noneMatch(f -> f.factor() == Factor.ML_SUITABILITY);
    }

    @Test
    void fairnessPenaltyAppliesAboveThreshold() {
        var nearlyFull = handler(1, Expertise.AUTO, 9, 10); // projected 100% > 85%
        ScoredCandidate c = engine.rank(claim(ClaimType.AUTO), List.of(nearlyFull), RankingWeights.defaults(), Map.of())
                .candidates().get(0);
        assertThat(c.fairnessAdjusted()).isTrue();
        assertThat(c.totalScore()).isCloseTo(c.scoreBeforeFairness() * 0.85, within(0.02));
        assertThat(c.explanation()).contains("workload balance");
    }

    @Test
    void explanationCitesActualFactors() {
        var specialist = handler(1, Expertise.AUTO, 1, 12);
        ScoredCandidate c = engine.rank(claim(ClaimType.AUTO), List.of(specialist), RankingWeights.defaults(), Map.of())
                .candidates().get(0);
        assertThat(c.explanation()).startsWith("Recommended because this handler");
        assertThat(c.explanation()).containsAnyOf("Auto specialist", "available capacity", "same city");
        assertThat(c.explanation()).doesNotContainIgnoringCase("model").doesNotContainIgnoringCase("probability");
    }

    @Test
    void sameCityScoresHigherProximity() {
        var local = handler(1, Expertise.AUTO, 0, 10, true, true, 10, "Pune");
        var far = handler(2, Expertise.AUTO, 0, 10, true, true, 10, "Delhi");
        var r = engine.rank(claim(ClaimType.AUTO), List.of(far, local), RankingWeights.defaults(), Map.of());
        assertThat(r.candidates().get(0).handler().code()).isEqualTo("A001");
    }
}
