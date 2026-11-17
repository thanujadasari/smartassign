package com.smartassign.core;

import com.smartassign.core.fairness.FairnessCalculator;
import com.smartassign.core.fairness.FairnessReport;
import com.smartassign.core.fairness.HandlerLoad;
import com.smartassign.domain.Expertise;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.smartassign.core.Fixtures.handler;
import static org.assertj.core.api.Assertions.assertThat;

class FairnessCalculatorTest {

    private final FairnessCalculator calc = new FairnessCalculator();

    @Test
    void expectedShareIsProportionalToCapacity() {
        FairnessReport r = calc.compute(List.of(
                new HandlerLoad(handler(1, Expertise.AUTO, 2, 20), 10, 0),
                new HandlerLoad(handler(2, Expertise.AUTO, 2, 10), 5, 0)), 0.85);
        assertThat(r.handlers().get(0).expectedAssignmentsByCapacity()).isEqualTo(10.0);
        assertThat(r.handlers().get(1).expectedAssignmentsByCapacity()).isEqualTo(5.0);
        assertThat(r.warnings()).noneMatch(w -> w.type().equals("CONCENTRATION"));
    }

    @Test
    void flagsOverCapacityHighUtilisationAndConcentration() {
        FairnessReport r = calc.compute(List.of(
                new HandlerLoad(handler(1, Expertise.AUTO, 12, 10), 0, 0),
                new HandlerLoad(handler(2, Expertise.AUTO, 9, 10), 20, 0),
                new HandlerLoad(handler(3, Expertise.AUTO, 0, 10), 0, 0),
                new HandlerLoad(handler(4, Expertise.AUTO, 0, 10), 0, 0)), 0.85);
        assertThat(r.overCapacityHandlers()).isEqualTo(1);
        assertThat(r.warnings()).extracting(w -> w.type())
                .contains("OVER_CAPACITY", "HIGH_UTILIZATION", "CONCENTRATION", "GROUP_IMBALANCE");
        assertThat(r.warnings().get(0).severity()).isEqualTo("CRITICAL");
    }

    @Test
    void unavailableHandlersAreNotComparedForConcentration() {
        FairnessReport r = calc.compute(List.of(
                new HandlerLoad(handler(1, Expertise.AUTO, 0, 10, false, true, 10, "Pune"), 0, 0),
                new HandlerLoad(handler(2, Expertise.AUTO, 3, 10), 6, 0)), 0.85);
        assertThat(r.availableHandlers()).isEqualTo(1);
        assertThat(r.warnings()).noneMatch(w -> w.type().equals("CONCENTRATION"));
    }
}
