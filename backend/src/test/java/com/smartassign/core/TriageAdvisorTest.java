package com.smartassign.core;

import com.smartassign.core.triage.TriageAdvisor;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Complexity;
import com.smartassign.domain.IncidentType;
import com.smartassign.domain.Priority;
import com.smartassign.domain.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TriageAdvisorTest {

    private final TriageAdvisor advisor = new TriageAdvisor();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private TriageAdvisor.Input input(ClaimType type, IncidentType incident, double loss, LocalDate incidentDate,
                                      boolean injury, boolean thirdParty, String desc, int others) {
        return new TriageAdvisor.Input(type, incident, loss, incidentDate, TODAY, injury, thirdParty, desc, others, false);
    }

    private TriageAdvisor.Input ongoing(TriageAdvisor.Input i) {
        return new TriageAdvisor.Input(i.claimType(), i.incidentType(), i.estimatedLoss(), i.incidentDate(), i.reportedOn(),
                i.injuryInvolved(), i.thirdPartyInvolved(), i.description(), i.otherClaimsByCustomerLast12Months(), true);
    }

    private static final String DESC = "Rear-ended at a traffic signal, bumper and tail lights damaged.";

    @Test
    void severityFollowsLossBands() {
        assertThat(advisor.suggest(input(ClaimType.AUTO, IncidentType.COLLISION, 114_999, TODAY, false, false, DESC, 0)).severity()).isEqualTo(Severity.LOW);
        assertThat(advisor.suggest(input(ClaimType.AUTO, IncidentType.COLLISION, 115_000, TODAY, false, false, DESC, 0)).severity()).isEqualTo(Severity.MEDIUM);
        assertThat(advisor.suggest(input(ClaimType.AUTO, IncidentType.COLLISION, 308_000, TODAY, false, false, DESC, 0)).severity()).isEqualTo(Severity.HIGH);
        assertThat(advisor.suggest(input(ClaimType.AUTO, IncidentType.COLLISION, 834_000, TODAY, false, false, DESC, 0)).severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void injuryWithThirdPartyRaisesLowComplexity() {
        var s = advisor.suggest(input(ClaimType.LIABILITY, IncidentType.ACCIDENT, 50_000, TODAY, true, true, DESC, 0));
        assertThat(s.complexity()).isEqualTo(Complexity.MEDIUM);
        assertThat(s.complexityReasons()).anyMatch(r -> r.basis().equals(TriageAdvisor.BASIS_ASSUMED));
    }

    @Test
    void cleanClaimGetsBaseFraudScoreAndNoFlags() {
        var s = advisor.suggest(input(ClaimType.AUTO, IncidentType.COLLISION, 90_000, TODAY.minusDays(2), false, false, DESC, 0));
        assertThat(s.fraudFlags()).isEmpty();
        assertThat(s.fraudRiskScore()).isEqualTo(TriageAdvisor.FRAUD_BASE);
    }

    @Test
    void redFlagsAddUpAndAreListed() {
        var s = advisor.suggest(input(ClaimType.AUTO, IncidentType.THEFT, 700_000, TODAY.minusDays(45), false, false, "Car stolen", 2));
        assertThat(s.fraudFlags()).extracting(TriageAdvisor.FraudFlag::code)
                .containsExactlyInAnyOrder("LATE_REPORT", "UNUSUALLY_HIGH_LOSS", "THEFT", "FREQUENT_CLAIMANT", "THIN_DESCRIPTION");
        assertThat(s.fraudRiskScore()).isEqualTo(0.70);
    }

    @Test
    void injuryFireMajorPropertyAndOngoingRiskAreHighPriority() {
        assertThat(advisor.suggest(input(ClaimType.AUTO, IncidentType.COLLISION, 60_000, TODAY, true, false, DESC, 0)).priority()).isEqualTo(Priority.HIGH);
        assertThat(advisor.suggest(input(ClaimType.PROPERTY, IncidentType.FIRE, 40_000, TODAY, false, false, DESC, 0)).priority()).isEqualTo(Priority.HIGH);
        assertThat(advisor.suggest(input(ClaimType.PROPERTY, IncidentType.FLOOD, 400_000, TODAY, false, false, DESC, 0)).priority()).isEqualTo(Priority.HIGH);
        assertThat(advisor.suggest(ongoing(input(ClaimType.PROPERTY, IncidentType.DAMAGE, 20_000, TODAY, false, false, DESC, 0))).priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void moderateDamageOrInvestigationIsMediumPriority() {
        assertThat(advisor.suggest(input(ClaimType.AUTO, IncidentType.COLLISION, 150_000, TODAY, false, true, DESC, 0)).priority()).isEqualTo(Priority.MEDIUM);
        assertThat(advisor.suggest(input(ClaimType.PROPERTY, IncidentType.STORM, 200_000, TODAY, false, false, DESC, 0)).priority()).isEqualTo(Priority.MEDIUM);
        assertThat(advisor.suggest(input(ClaimType.AUTO, IncidentType.THEFT, 50_000, TODAY, false, false, DESC, 0)).priority()).isEqualTo(Priority.MEDIUM);
    }

    @Test
    void minorDamageIsLowPriority() {
        var s = advisor.suggest(input(ClaimType.AUTO, IncidentType.DAMAGE, 12_000, TODAY, false, false, DESC, 0));
        assertThat(s.priority()).isEqualTo(Priority.LOW);
        assertThat(s.priorityReasons()).singleElement().satisfies(r -> assertThat(r.text()).startsWith("Minor vehicle damage"));
    }
}
